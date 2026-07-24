package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.sentiment.*;
import com.foodadvisor.dto.ReviewAnalysisResultVO;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewAnalysis;
import com.foodadvisor.entity.ReviewIssueCategory;
import com.foodadvisor.mapper.ReviewAnalysisMapper;
import com.foodadvisor.mapper.ReviewIssueCategoryMapper;
import com.foodadvisor.mapper.ReviewIssueRelationMapper;
import com.foodadvisor.mapper.ReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商家评论情感分析服务。
 *
 * 负责：
 * 1. 从数据库查询评论及其 AI 分析结果
 * 2. 在应用层做聚合统计（情感分布、维度占比、关键词排名、差评归类）
 * 3. 调用 AI 服务生成口碑摘要（可选）
 */
@Service
public class MerchantSentimentService {

    private static final Logger log = LoggerFactory.getLogger(MerchantSentimentService.class);

    private final ReviewMapper reviewMapper;
    private final ReviewAnalysisMapper reviewAnalysisMapper;
    private final AIClientService aiClientService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ReviewIssueCategoryMapper issueCategoryMapper;
    private final ReviewIssueRelationMapper issueRelationMapper;

    /** 维度中文名 */
    private static final Map<String, String> DIM_NAMES = Map.of(
            "TASTE", "口味",
            "ENVIRONMENT", "环境",
            "SERVICE", "服务",
            "PRICE", "价格",
            "SPEED", "速度",
            "PORTION", "分量",
            "HYGIENE", "卫生",
            "QUEUE_TIME", "排队",
            "PARKING", "停车"
    );

    /** 差评归因类别中文名 */
    private static final Map<String, String> ISSUE_NAMES = Map.of(
            "HYGIENE", "卫生问题",
            "SERVICE_ATTITUDE", "服务态度",
            "SERVING_SPEED", "上菜速度",
            "TASTE", "菜品口味",
            "PRICE", "价格问题",
            "PORTION", "分量问题",
            "QUEUE", "排队时间",
            "ENVIRONMENT", "环境问题",
            "OTHER", "其他问题"
    );

    public MerchantSentimentService(
            ReviewMapper reviewMapper,
            ReviewAnalysisMapper reviewAnalysisMapper,
            AIClientService aiClientService,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            ReviewIssueCategoryMapper issueCategoryMapper,
            ReviewIssueRelationMapper issueRelationMapper
    ) {
        this.reviewMapper = reviewMapper;
        this.reviewAnalysisMapper = reviewAnalysisMapper;
        this.aiClientService = aiClientService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.issueCategoryMapper = issueCategoryMapper;
        this.issueRelationMapper = issueRelationMapper;
    }

    // ============================================
    // 汇总统计
    // ============================================

    public SentimentSummaryVO getSummary(Long merchantId, String timeRange) {
        OffsetDateTime since = parseTimeRange(timeRange);

        // 1. 查询时间段内的所有评价
        List<Review> reviews = queryReviews(merchantId, since);
        int totalReviews = reviews.size();

        // 2. 查询已分析的评价
        List<Long> reviewIds = reviews.stream().map(Review::getId).collect(Collectors.toList());
        Map<Long, ReviewAnalysis> analysisMap = queryAnalysisMap(reviewIds);
        int totalAnalyzed = analysisMap.size();

        // 3. 汇总统计
        SentimentSummaryVO.SentimentSummaryVOBuilder builder = SentimentSummaryVO.builder()
                .totalReviews(totalReviews)
                .totalAnalyzed(totalAnalyzed);

        if (totalAnalyzed == 0) {
            return builder
                    .positiveRate(0.0).negativeRate(0.0)
                    .positiveTrend(0.0).negativeTrend(0.0)
                    .topComplaintDimension("-").topComplaintCount(0)
                    .sentimentDistribution(Map.of())
                    .dimensions(Map.of())
                    .positiveKeywords(List.of())
                    .complaintIssues(List.of())
                    .updateTime(OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .build();
        }

        // 3a. 情感分布
        Map<String, Long> sentimentCounts = new LinkedHashMap<>();
        sentimentCounts.put("POSITIVE", 0L);
        sentimentCounts.put("NEGATIVE", 0L);
        sentimentCounts.put("NEUTRAL", 0L);
        sentimentCounts.put("MIXED", 0L);

        for (ReviewAnalysis ra : analysisMap.values()) {
            String s = ra.getSentiment() != null ? ra.getSentiment() : "NEUTRAL";
            sentimentCounts.merge(s, 1L, Long::sum);
        }

        Map<String, SentimentSummaryVO.SentimentCountVO> dist = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : sentimentCounts.entrySet()) {
            double pct = totalAnalyzed > 0 ? (e.getValue() * 100.0 / totalAnalyzed) : 0;
            dist.put(e.getKey(), SentimentSummaryVO.SentimentCountVO.builder()
                    .count(e.getValue().intValue())
                    .percentage(Math.round(pct * 10.0) / 10.0)
                    .build());
        }

        double positiveRate = dist.containsKey("POSITIVE")
                ? dist.get("POSITIVE").getPercentage() : 0;
        double negativeRate = dist.containsKey("NEGATIVE")
                ? dist.get("NEGATIVE").getPercentage() : 0;

        // 3b. 维度统计
        Map<String, SentimentDimensionVO> dimMap = computeDimensions(analysisMap.values(), totalAnalyzed);

        // 3c. 关键词排名
        List<SentimentKeywordVO> posKeywords = computeTopKeywords(analysisMap.values(), 10);

        // 3d. 差评问题归类
        List<SentimentIssueVO> issues = computeTopIssues(analysisMap.values(), totalAnalyzed);

        // 3e. 主要差评维度
        String topDim = "-";
        int topDimCount = 0;
        if (!issues.isEmpty()) {
            topDim = issues.get(0).getCategoryName();
            topDimCount = issues.get(0).getCount();
        }

        builder.positiveRate(positiveRate)
                .negativeRate(negativeRate)
                .positiveTrend(0.0)   // TODO: 对比上周期计算
                .negativeTrend(0.0)
                .topComplaintDimension(topDim)
                .topComplaintCount(topDimCount)
                .sentimentDistribution(dist)
                .dimensions(dimMap)
                .positiveKeywords(posKeywords)
                .complaintIssues(issues)
                .updateTime(OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        return builder.build();
    }

    // ============================================
    // 评论明细列表（分页）
    // ============================================

    public SentimentReviewPageVO getReviewPage(
            Long merchantId, String timeRange,
            String sentiment, String dimension, String keyword,
            int page, int pageSize
    ) {
        OffsetDateTime since = parseTimeRange(timeRange);

        // 查询所有评价 + 分析结果
        List<Review> allReviews = queryReviews(merchantId, since);
        List<Long> reviewIds = allReviews.stream().map(Review::getId).collect(Collectors.toList());
        Map<Long, ReviewAnalysis> analysisMap = queryAnalysisMap(reviewIds);

        // 转换为 VO 并过滤
        List<SentimentReviewItemVO> items = new ArrayList<>();
        for (Review rv : allReviews) {
            ReviewAnalysis ra = analysisMap.get(rv.getId());
            SentimentReviewItemVO vo = toItemVO(rv, ra);

            // 跳过未分析的评价（可选：也展示，但无情感数据）
            if (ra == null && (sentiment != null || dimension != null)) {
                continue; // 如果有筛选条件且未分析，跳过
            }

            // 情感筛选
            if (sentiment != null && !sentiment.isEmpty()
                    && !sentiment.equalsIgnoreCase(vo.getSentiment())) {
                continue;
            }

            // 维度筛选（aspects 中包含指定 category）
            if (dimension != null && !dimension.isEmpty()
                    && vo.getAspects() != null) {
                boolean match = vo.getAspects().stream()
                        .anyMatch(a -> dimension.equalsIgnoreCase(a.getCategory()));
                if (!match) continue;
            }

            // 关键词搜索
            if (keyword != null && !keyword.isEmpty()) {
                boolean kwMatch = vo.getKeywords() != null && vo.getKeywords().stream()
                        .anyMatch(k -> k.contains(keyword));
                boolean contentMatch = rv.getContent() != null
                        && rv.getContent().contains(keyword);
                if (!kwMatch && !contentMatch) continue;
            }

            items.add(vo);
        }

        // 分页
        long totalCount = items.size();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        List<SentimentReviewItemVO> pageItems = fromIndex < items.size()
                ? items.subList(fromIndex, toIndex)
                : List.of();

        return SentimentReviewPageVO.builder()
                .records(pageItems)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    // ============================================
    // 触发批量分析
    // ============================================

    public Map<String, Object> triggerBatchAnalysis(Long merchantId, String timeRange, String analysisMode) {
        OffsetDateTime since = parseTimeRange(timeRange);
        List<Review> reviews = queryReviews(merchantId, since);
        List<Long> reviewIds = reviews.stream().map(Review::getId).collect(Collectors.toList());
        Map<Long, ReviewAnalysis> analysisMap = queryAnalysisMap(reviewIds);

        // 筛选未分析的评价
        List<Review> unanalyzed = reviews.stream()
                .filter(r -> !analysisMap.containsKey(r.getId()))
                .collect(Collectors.toList());

        int successCount = 0;
        int failCount = 0;

        if (!unanalyzed.isEmpty()) {
            // 分批（每批最多 100 条）
            int batchSize = 100;

            for (int i = 0; i < unanalyzed.size(); i += batchSize) {
                int end = Math.min(i + batchSize, unanalyzed.size());
                List<Review> batch = unanalyzed.subList(i, end);

                List<Map<String, Object>> batchRequests = batch.stream()
                        .map(r -> {
                            Map<String, Object> req = new LinkedHashMap<>();
                            req.put("reviewId", r.getId());
                            req.put("merchantId", r.getMerchantId());
                            req.put("reviewVersion", r.getCurrentVersion() != null ? r.getCurrentVersion() : 1);
                            req.put("content", r.getContent());
                            return req;
                        })
                        .collect(Collectors.toList());

                try {
                    JsonNode response = aiClientService.batchAnalyzeReviews(batchRequests, analysisMode);
                    int persisted = persistBatchResults(response, batch);
                    successCount += persisted;
                    failCount += (batch.size() - persisted);
                } catch (Exception e) {
                    log.error("批量分析失败: merchantId={}, batch={}-{}", merchantId, i, end, e);
                    failCount += batch.size();
                }
            }
        }

        // 对于已分析但缺少 issue_relations 的评价，从已有分析结果中补充归因关联。
        // 这些评价之前由旧版流程分析，analysis 数据已存在但未写入 review_issue_relations。
        int backfilled = backfillMissingIssueRelations(merchantId, since);

        if (unanalyzed.isEmpty() && backfilled == 0) {
            return Map.of("analyzedCount", 0, "message", "所有评价已分析完成");
        }

        String message = successCount > 0
                ? "分析完成！成功 " + successCount + " 条"
                : "差评归因数据已补充 " + backfilled + " 条";
        return Map.of(
                "analyzedCount", successCount,
                "failCount", failCount,
                "totalUnanalyzed", unanalyzed.size(),
                "backfilledIssueRelations", backfilled,
                "message", message
        );
    }

    // ============================================
    // 单条评论详情
    // ============================================

    /**
     * 获取单条评论的情感分析结果，供详情弹窗使用。
     *
     * @param merchantId 商家 ID（安全校验）
     * @param reviewId   评价 ID
     * @return 分析结果 VO，评价不存在或不属于该商家时返回 null
     */
    public SentimentReviewItemVO getReviewItem(Long merchantId, Long reviewId) {
        Review rv = reviewMapper.selectOne(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getId, reviewId)
                        .eq(Review::getMerchantId, merchantId)
                        .eq(Review::getStatus, "PUBLISHED")
        );
        if (rv == null) return null;

        Map<Long, ReviewAnalysis> analysisMap = queryAnalysisMap(List.of(reviewId));
        ReviewAnalysis ra = analysisMap.get(reviewId);
        return toItemVO(rv, ra);
    }

    // ============================================
    // 私有辅助方法
    // ============================================

    private List<Review> queryReviews(Long merchantId, OffsetDateTime since) {
        LambdaQueryWrapper<Review> qw = new LambdaQueryWrapper<>();
        qw.eq(Review::getMerchantId, merchantId)
                .eq(Review::getStatus, "PUBLISHED");
        if (since != null) {
            qw.ge(Review::getReviewTime, since);
        }
        qw.orderByDesc(Review::getReviewTime);
        return reviewMapper.selectList(qw);
    }

    private Map<Long, ReviewAnalysis> queryAnalysisMap(List<Long> reviewIds) {
        if (reviewIds.isEmpty()) return Map.of();
        LambdaQueryWrapper<ReviewAnalysis> qw = new LambdaQueryWrapper<>();
        qw.in(ReviewAnalysis::getReviewId, reviewIds)
                .eq(ReviewAnalysis::getStatus, "SUCCESS");
        List<ReviewAnalysis> list = reviewAnalysisMapper.selectList(qw);
        // 每个 reviewId 取最新的分析结果
        return list.stream()
                .collect(Collectors.toMap(
                        ReviewAnalysis::getReviewId,
                        ra -> ra,
                        (a, b) -> a.getAnalysisVersion() >= b.getAnalysisVersion() ? a : b
                ));
    }

    private SentimentReviewItemVO toItemVO(Review rv, ReviewAnalysis ra) {
        SentimentReviewItemVO.SentimentReviewItemVOBuilder b = SentimentReviewItemVO.builder()
                .reviewId(rv.getId())
                .merchantId(rv.getMerchantId())
                .rating(rv.getRating() != null ? rv.getRating().intValue() : 3)
                .content(rv.getContent())
                .reviewTime(rv.getReviewTime() != null
                        ? rv.getReviewTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : null);

        if (ra != null) {
            b.sentiment(ra.getSentiment() != null ? ra.getSentiment() : "NEUTRAL")
                    .confidence(ra.getConfidence() != null ? ra.getConfidence().doubleValue() : 0.5)
                    .keywords(parseJsonList(ra.getKeywords()))
                    .aspects(parseAspects(ra.getAspects()))
                    .issueCategories(parseIssueCategories(ra.getNegativeReason()))
                    .businessTraceId(ra.getBusinessTraceId());
        } else {
            b.sentiment("UNKNOWN")
                    .confidence(0.0)
                    .keywords(List.of())
                    .aspects(List.of())
                    .issueCategories(List.of());
        }

        return b.build();
    }

    private Map<String, SentimentDimensionVO> computeDimensions(
            Collection<ReviewAnalysis> analyses, int total
    ) {
        // 聚合每个维度的正面/负面/中性计数
        Map<String, int[]> agg = new LinkedHashMap<>();
        // key -> [positive, negative, neutral, mentioned]
        for (String dim : List.of("SERVICE", "TASTE", "PRICE", "ENVIRONMENT")) {
            agg.put(dim, new int[4]);
        }

        for (ReviewAnalysis ra : analyses) {
            List<ReviewAnalysisResultVO.AspectVO> aspects = parseAspects(ra.getAspects());
            if (aspects == null) continue;
            for (ReviewAnalysisResultVO.AspectVO asp : aspects) {
                int[] counts = agg.get(asp.getCategory());
                if (counts == null) continue;
                counts[3]++; // mentioned
                String s = asp.getSentiment();
                if ("POSITIVE".equalsIgnoreCase(s)) counts[0]++;
                else if ("NEGATIVE".equalsIgnoreCase(s)) counts[1]++;
                else counts[2]++;
            }
        }

        Map<String, SentimentDimensionVO> result = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : agg.entrySet()) {
            int[] c = e.getValue();
            int mentioned = c[3];
            if (mentioned == 0) {
                result.put(e.getKey(), SentimentDimensionVO.builder()
                        .key(e.getKey()).label(DIM_NAMES.getOrDefault(e.getKey(), e.getKey()))
                        .positivePct(0.0).neutralPct(0.0).negativePct(0.0)
                        .positiveCount(0).negativeCount(0).coverage(0.0)
                        .build());
                continue;
            }
            result.put(e.getKey(), SentimentDimensionVO.builder()
                    .key(e.getKey()).label(DIM_NAMES.getOrDefault(e.getKey(), e.getKey()))
                    .positivePct(round1(c[0] * 100.0 / mentioned))
                    .neutralPct(round1(c[2] * 100.0 / mentioned))
                    .negativePct(round1(c[1] * 100.0 / mentioned))
                    .positiveCount(c[0]).negativeCount(c[1])
                    .coverage(round1(mentioned * 100.0 / total))
                    .build());
        }
        return result;
    }

    private List<SentimentKeywordVO> computeTopKeywords(
            Collection<ReviewAnalysis> analyses, int topN
    ) {
        Map<String, Integer> kwCount = new LinkedHashMap<>();

        for (ReviewAnalysis ra : analyses) {
            // 来源1：LLM/hybrid 模式提取的关键词
            List<String> kws = parseJsonList(ra.getKeywords());
            if (kws != null) {
                for (String kw : kws) {
                    kwCount.merge(kw, 1, Integer::sum);
                }
            }

            // 来源2：local 模式 — 从 aspects 的正面维度中生成关键词
            // 例如：SERVICE+POSITIVE → "服务好"，TASTE+POSITIVE → "口味好"
            if (kws == null || kws.isEmpty()) {
                List<ReviewAnalysisResultVO.AspectVO> aspects = parseAspects(ra.getAspects());
                if (aspects != null) {
                    for (ReviewAnalysisResultVO.AspectVO asp : aspects) {
                        if ("POSITIVE".equalsIgnoreCase(asp.getSentiment())) {
                            String label = DIM_NAMES.getOrDefault(asp.getCategory(), null);
                            if (label != null) {
                                kwCount.merge(label + "好", 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }

        return kwCount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(topN)
                .map(e -> SentimentKeywordVO.builder().word(e.getKey()).count(e.getValue()).build())
                .collect(Collectors.toList());
    }

    private List<SentimentIssueVO> computeTopIssues(
            Collection<ReviewAnalysis> analyses, int total
    ) {
        // 从两类来源统计差评问题：
        // 1. negativeReason 字段（llm/hybrid 模式有值）
        // 2. aspects 中各维度的负面情感（local 模式用这个）
        Map<String, Integer> issueCount = new LinkedHashMap<>();

        for (ReviewAnalysis ra : analyses) {
            // 来源1：negativeReason（llm/hybrid 模式）
            String reason = ra.getNegativeReason();
            if (reason != null && !reason.isEmpty()) {
                issueCount.merge(reason, 1, Integer::sum);
            }

            // 来源2：aspects 中标记为 NEGATIVE 的维度（local 模式的主要来源）
            List<ReviewAnalysisResultVO.AspectVO> aspects = parseAspects(ra.getAspects());
            if (aspects != null) {
                for (ReviewAnalysisResultVO.AspectVO asp : aspects) {
                    if ("NEGATIVE".equalsIgnoreCase(asp.getSentiment())) {
                        // 将维度类别映射到差评归因类别
                        String issueCategory = aspectToIssueCategory(asp.getCategory());
                        if (issueCategory != null) {
                            issueCount.merge(issueCategory, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        return issueCount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(8)
                .map(e -> SentimentIssueVO.builder()
                        .category(e.getKey())
                        .categoryName(ISSUE_NAMES.getOrDefault(e.getKey(), e.getKey()))
                        .count(e.getValue())
                        .percentage(round1(e.getValue() * 100.0 / Math.max(total, 1)))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 将 aspects 维度类别映射到差评归因类别编码。
     * 例如：SERVICE → SERVICE_ATTITUDE, TASTE → TASTE, SPEED → SERVING_SPEED
     */
    private String aspectToIssueCategory(String aspectCategory) {
        if (aspectCategory == null) return null;
        return switch (aspectCategory.toUpperCase()) {
            case "SERVICE" -> "SERVICE_ATTITUDE";
            case "TASTE" -> "TASTE";
            case "PRICE" -> "PRICE";
            case "ENVIRONMENT" -> "ENVIRONMENT";
            case "SPEED" -> "SERVING_SPEED";
            case "PORTION" -> "PORTION";
            case "HYGIENE" -> "HYGIENE";
            case "QUEUE_TIME" -> "QUEUE";
            case "PARKING" -> "OTHER";
            default -> null;
        };
    }

    // ---- JSON 解析 ----

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ReviewAnalysisResultVO.AspectVO> parseAspects(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    new TypeReference<List<ReviewAnalysisResultVO.AspectVO>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ReviewAnalysisResultVO.IssueCategoryVO> parseIssueCategories(String json) {
        // negativeReason 存储为逗号分隔的类别编码，这里仅从 negativeReason 字段解析
        if (json == null || json.isBlank()) return List.of();
        try {
            // 先尝试 JSON 解析
            return objectMapper.readValue(json,
                    new TypeReference<List<ReviewAnalysisResultVO.IssueCategoryVO>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    // ---- 工具方法 ----

    /**
     * 解析批量分析返回的 JSON，将每条结果持久化到 review_analysis 表。
     * 返回成功持久化的条数。
     *
     * 使用原生 SQL 以确保 PostgreSQL JSONB 字段正确处理，
     * 与 {@link ReviewService#saveAnalysis(ReviewAnalysis)} 保持一致。
     */
    private int persistBatchResults(JsonNode response, List<Review> batch) {
        JsonNode results = response.path("results");
        if (!results.isArray()) {
            log.warn("AI 批量分析返回结果缺少 results 数组，response keys: {}",
                    response.fieldNames().hasNext()
                            ? response.fieldNames().next() : "empty");
            return 0;
        }

        Map<Long, Review> reviewMap = batch.stream()
                .collect(Collectors.toMap(Review::getId, r -> r, (a, b) -> a));

        // 预先查询这批评价的现有分析记录（包含所有状态，不只是 SUCCESS），
        // 用于正确计算 analysisVersion，避免唯一约束冲突。
        List<Long> reviewIds = batch.stream().map(Review::getId).collect(Collectors.toList());
        Map<Long, ReviewAnalysis> existingAnalysisMap = queryAllAnalysisMap(reviewIds);

        int count = 0;
        for (JsonNode item : results) {
            try {
                long reviewId = item.path("reviewId").asLong();
                Review review = reviewMap.get(reviewId);
                if (review == null) continue;

                ReviewAnalysis existing = existingAnalysisMap.get(reviewId);
                int nextVersion = existing != null ? existing.getAnalysisVersion() + 1 : 1;
                int reviewVersion = review.getCurrentVersion() != null
                        ? review.getCurrentVersion() : 1;

                String sentiment = item.path("sentiment").asText("NEUTRAL");
                BigDecimal confidence = BigDecimal.valueOf(
                        item.path("confidence").asDouble(0.5));
                boolean lowConfidence = item.path("lowConfidence").asBoolean(false);
                String keywordsJson = item.path("keywords").toString();
                String aspectsJson = item.path("aspects").toString();
                String negativeReason = item.path("negativeReason").asText(null);
                String modelName = item.path("modelName").asText(null);
                String modelVersion = item.path("modelVersion").asText(null);
                String businessTraceId = item.path("businessTraceId").asText(null);

                if (existing != null) {
                    // 更新已有记录
                    jdbcTemplate.update(
                            """
                            UPDATE review_analysis
                            SET review_version = ?,
                                analysis_version = ?,
                                sentiment = ?,
                                confidence = ?,
                                low_confidence = ?,
                                keywords = ?::jsonb,
                                aspects = ?::jsonb,
                                negative_reason = ?,
                                model_name = ?,
                                model_version = ?,
                                business_trace_id = ?,
                                status = 'SUCCESS',
                                error_message = NULL,
                                completed_at = CURRENT_TIMESTAMP,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE review_id = ?
                              AND review_version = ?
                              AND analysis_version = ?
                            """,
                            reviewVersion, nextVersion,
                            sentiment, confidence, lowConfidence,
                            keywordsJson, aspectsJson,
                            negativeReason, modelName, modelVersion,
                            businessTraceId,
                            reviewId, existing.getReviewVersion(),
                            existing.getAnalysisVersion()
                    );
                } else {
                    // 插入新记录
                    jdbcTemplate.update(
                            """
                            INSERT INTO review_analysis (
                                review_id, review_version, analysis_version,
                                sentiment, confidence, low_confidence,
                                keywords, aspects, negative_reason,
                                model_name, model_version,
                                business_trace_id, status, completed_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, 'SUCCESS', CURRENT_TIMESTAMP)
                            """,
                            reviewId, reviewVersion, nextVersion,
                            sentiment, confidence, lowConfidence,
                            keywordsJson, aspectsJson,
                            negativeReason, modelName, modelVersion,
                            businessTraceId
                    );
                }
                // 保存差评归因关联（与 ReviewController.analyzeReview 行为一致）
                saveIssueRelationsFromResult(reviewId, reviewVersion, item);
                count++;
            } catch (Exception e) {
                log.warn("持久化分析结果失败: reviewId={}", item.path("reviewId").asText(), e);
            }
        }
        return count;
    }

    /**
     * 从 AI 分析结果中解析 issueCategories，写入 review_issue_relations。
     * 与 ReviewController.analyzeReview() 中的归因关联持久化逻辑保持一致，
     * 确保商家端"差评归因钻取"功能能查到批量分析的评论。
     */
    private void saveIssueRelationsFromResult(
            Long reviewId, int reviewVersion, JsonNode item
    ) {
        JsonNode issueCategories = item.path("issueCategories");
        if (!issueCategories.isArray()) return;

        // 删除旧关联（同一 review_id）
        jdbcTemplate.update(
                "DELETE FROM review_issue_relations WHERE review_id = ?",
                reviewId);

        for (JsonNode issueNode : issueCategories) {
            try {
                String catCode = issueNode.path("category").asText(null);
                if (catCode == null || catCode.isBlank()) continue;

                // 通过类别编码查字典表取得 ID
                ReviewIssueCategory cat = issueCategoryMapper.selectOne(
                        new LambdaQueryWrapper<ReviewIssueCategory>()
                                .eq(ReviewIssueCategory::getCode, catCode)
                                .eq(ReviewIssueCategory::getStatus, "ACTIVE")
                );
                if (cat == null) continue;

                BigDecimal conf = issueNode.has("confidence")
                        ? BigDecimal.valueOf(issueNode.path("confidence").asDouble(0.5))
                        : BigDecimal.valueOf(0.5);
                String evidence = issueNode.has("evidenceText")
                        && !issueNode.path("evidenceText").isNull()
                        ? issueNode.path("evidenceText").asText()
                        : null;

                jdbcTemplate.update(
                        """
                        INSERT INTO review_issue_relations (
                            review_id, review_version, issue_category_id,
                            confidence, evidence_text
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                        reviewId, reviewVersion, cat.getId(), conf, evidence);
            } catch (Exception e) {
                log.warn("保存差评归因关联失败: reviewId={}, category={}",
                        reviewId, issueNode.path("category").asText(), e);
            }
        }
    }

    /**
     * 从已有 review_analysis 数据中补充缺失的 review_issue_relations。
     *
     * 旧版批量分析或单条分析流程只写入 review_analysis 表，未写入
     * review_issue_relations，导致商家端差评钻取为空。
     * 本方法解析 aspects 中 NEGATIVE 维度和 negativeReason 字段，
     * 生成对应的归因关联记录，无需再次调用 AI。
     *
     * @return 补充的归因关联条数
     */
    private int backfillMissingIssueRelations(Long merchantId, OffsetDateTime since) {
        // 查询商家评价中已分析但缺少归因关联的 review_id
        String findMissingSql = """
                SELECT ra.review_id, ra.review_version, ra.aspects, ra.negative_reason
                FROM review_analysis ra
                JOIN reviews r ON r.id = ra.review_id
                WHERE r.merchant_id = ?
                  AND ra.status = 'SUCCESS'
                  AND NOT EXISTS (
                      SELECT 1 FROM review_issue_relations rir
                      WHERE rir.review_id = ra.review_id
                  )
                """;

        List<Long> missingIds = new ArrayList<>();
        if (since != null) {
            findMissingSql += " AND r.review_time >= ?";
            missingIds = jdbcTemplate.query(findMissingSql,
                    (rs, rowNum) -> rs.getLong("review_id"),
                    merchantId, since);
        } else {
            missingIds = jdbcTemplate.query(findMissingSql,
                    (rs, rowNum) -> rs.getLong("review_id"),
                    merchantId);
        }

        if (missingIds.isEmpty()) return 0;

        int backfilled = 0;
        for (Long reviewId : missingIds) {
            try {
                Map<Long, ReviewAnalysis> analysisMap = queryAllAnalysisMap(List.of(reviewId));
                ReviewAnalysis ra = analysisMap.get(reviewId);
                if (ra == null) continue;

                int count = 0;

                // 来源1：从 aspects 中提取 NEGATIVE 维度 → 归因类别
                List<ReviewAnalysisResultVO.AspectVO> aspects = parseAspects(ra.getAspects());
                if (aspects != null) {
                    for (ReviewAnalysisResultVO.AspectVO asp : aspects) {
                        if ("NEGATIVE".equalsIgnoreCase(asp.getSentiment())) {
                            String issueCategory = aspectToIssueCategory(asp.getCategory());
                            if (issueCategory != null) {
                                ReviewIssueCategory cat = issueCategoryMapper.selectOne(
                                        new LambdaQueryWrapper<ReviewIssueCategory>()
                                                .eq(ReviewIssueCategory::getCode, issueCategory)
                                                .eq(ReviewIssueCategory::getStatus, "ACTIVE")
                                );
                                if (cat != null) {
                                    jdbcTemplate.update(
                                            """
                                            INSERT INTO review_issue_relations (
                                                review_id, review_version, issue_category_id,
                                                confidence, evidence_text
                                            ) VALUES (?, ?, ?, ?, ?)
                                            ON CONFLICT DO NOTHING
                                            """,
                                            reviewId,
                                            ra.getReviewVersion() != null ? ra.getReviewVersion() : 1,
                                            cat.getId(),
                                            ra.getConfidence() != null ? ra.getConfidence() : BigDecimal.valueOf(0.5),
                                            asp.getText()
                                    );
                                    count++;
                                }
                            }
                        }
                    }
                }

                // 来源2：从 negativeReason 字段提取归因类别
                String negReason = ra.getNegativeReason();
                if (count == 0 && negReason != null && !negReason.isBlank()) {
                    ReviewIssueCategory cat = issueCategoryMapper.selectOne(
                            new LambdaQueryWrapper<ReviewIssueCategory>()
                                    .eq(ReviewIssueCategory::getCode, negReason)
                                    .eq(ReviewIssueCategory::getStatus, "ACTIVE")
                    );
                    if (cat != null) {
                        jdbcTemplate.update(
                                """
                                INSERT INTO review_issue_relations (
                                    review_id, review_version, issue_category_id,
                                    confidence, evidence_text
                                ) VALUES (?, ?, ?, ?, NULL)
                                ON CONFLICT DO NOTHING
                                """,
                                reviewId,
                                ra.getReviewVersion() != null ? ra.getReviewVersion() : 1,
                                cat.getId(),
                                ra.getConfidence() != null ? ra.getConfidence() : BigDecimal.valueOf(0.5)
                        );
                        count++;
                    }
                }

                backfilled += count;
            } catch (Exception e) {
                log.warn("补充差评归因关联失败: reviewId={}", reviewId, e);
            }
        }

        if (backfilled > 0) {
            log.info("补充差评归因关联完成: merchantId={}, backfilled={}条, 涉及{}条评价",
                    merchantId, backfilled, missingIds.size());
        }
        return backfilled;
    }

    /**
     * 查询评论的所有分析记录（包含所有状态，不限于 SUCCESS）。
     * 用于确定 analysisVersion 以避免唯一约束冲突。
     */
    private Map<Long, ReviewAnalysis> queryAllAnalysisMap(List<Long> reviewIds) {
        if (reviewIds.isEmpty()) return Map.of();
        LambdaQueryWrapper<ReviewAnalysis> qw = new LambdaQueryWrapper<>();
        qw.in(ReviewAnalysis::getReviewId, reviewIds);
        List<ReviewAnalysis> list = reviewAnalysisMapper.selectList(qw);
        return list.stream()
                .collect(Collectors.toMap(
                        ReviewAnalysis::getReviewId,
                        ra -> ra,
                        (a, b) -> a.getAnalysisVersion() >= b.getAnalysisVersion() ? a : b
                ));
    }

    private OffsetDateTime parseTimeRange(String range) {
        if (range == null || "all".equals(range)) return null;
        int days = switch (range) {
            case "7d" -> 7;
            case "30d" -> 30;
            case "90d" -> 90;
            default -> 30;
        };
        return OffsetDateTime.now().minusDays(days);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
