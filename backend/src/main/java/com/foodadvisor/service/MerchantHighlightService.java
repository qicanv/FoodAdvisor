package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.highlight.HighlightEvidenceVO;
import com.foodadvisor.dto.highlight.MerchantHighlightVO;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.MerchantHighlight;
import com.foodadvisor.entity.MerchantHighlightEvidence;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewAnalysis;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.MerchantHighlightEvidenceMapper;
import com.foodadvisor.mapper.MerchantHighlightMapper;
import com.foodadvisor.entity.AiRequestTraceStage;
import com.foodadvisor.trace.AiTraceContext;
import org.springframework.beans.factory.annotation.Autowired;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.ReviewAnalysisMapper;
import com.foodadvisor.mapper.ReviewMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商家亮点挖掘服务（EPIC-02 Story 5）
 *
 * 从正面评价中提取顾客经常认可的招牌菜、环境特色、服务特点、
 * 价格优势和品牌特色，并按提及次数和好评比例生成商家亮点。
 *
 * 亮点生成后落库缓存，用户端读取不再调用模型；
 * 新增评论达到阈值或亮点过期后可刷新。
 */
@Service
public class MerchantHighlightService {

    /** 生成亮点所需的最少正面评论数（验收准则 5） */
    private static final int MIN_POSITIVE_COUNT = 5;

    /** 单次送入模型的最大正面评价条数，防止提示词过长 */
    private static final int MAX_REVIEWS_FOR_HIGHLIGHT = 100;

    /** 亮点最长有效天数，超过后允许刷新 */
    private static final int REFRESH_MAX_AGE_DAYS = 7;

    private final MerchantHighlightMapper highlightMapper;
    private final MerchantHighlightEvidenceMapper evidenceMapper;
    private final ReviewMapper reviewMapper;
    private final ReviewAnalysisMapper reviewAnalysisMapper;
    private final MerchantMapper merchantMapper;
    private final AIClientService aiClientService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiRequestTraceService traceService;

    public MerchantHighlightService(
            MerchantHighlightMapper highlightMapper,
            MerchantHighlightEvidenceMapper evidenceMapper,
            ReviewMapper reviewMapper,
            ReviewAnalysisMapper reviewAnalysisMapper,
            MerchantMapper merchantMapper,
            AIClientService aiClientService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this(highlightMapper, evidenceMapper, reviewMapper, reviewAnalysisMapper, merchantMapper,
                aiClientService, jdbcTemplate, objectMapper, null);
    }

    @Autowired
    public MerchantHighlightService(
            MerchantHighlightMapper highlightMapper,
            MerchantHighlightEvidenceMapper evidenceMapper,
            ReviewMapper reviewMapper,
            ReviewAnalysisMapper reviewAnalysisMapper,
            MerchantMapper merchantMapper,
            AIClientService aiClientService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AiRequestTraceService traceService
    ) {
        this.highlightMapper = highlightMapper;
        this.evidenceMapper = evidenceMapper;
        this.reviewMapper = reviewMapper;
        this.reviewAnalysisMapper = reviewAnalysisMapper;
        this.merchantMapper = merchantMapper;
        this.aiClientService = aiClientService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.traceService = traceService;
    }

    // ==================== 公开方法 ====================

    /**
     * 读取商家当前可展示的亮点列表 — 只查库，绝不调用模型。
     *
     * @param merchantId 商家ID
     * @return 亮点列表 VO；无数据时返回空列表并附带状态说明
     */
    public List<MerchantHighlightVO> getDisplayHighlights(Long merchantId) {
        // 查询当前活跃的亮点
        List<MerchantHighlight> highlights = highlightMapper.selectList(
                new LambdaQueryWrapper<MerchantHighlight>()
                        .eq(MerchantHighlight::getMerchantId, merchantId)
                        .eq(MerchantHighlight::getStatus, "ACTIVE")
                        .orderByDesc(MerchantHighlight::getMentionCount)
        );

        // 统计当前可用正面评价数
        int availablePositive = countPositiveReviews(merchantId);

        if (highlights.isEmpty()) {
            // 从未生成过亮点，返回空状态 VO
            MerchantHighlightVO emptyVO = new MerchantHighlightVO();
            emptyVO.setMerchantId(merchantId);
            emptyVO.setStatus("NONE");
            emptyVO.setStatusMessage(
                    availablePositive < MIN_POSITIVE_COUNT
                            ? "正面评论数量不足，至少需要 " + MIN_POSITIVE_COUNT + " 条正面评价才能生成亮点"
                            : "亮点尚未生成，请点击刷新亮点按钮触发生成"
            );
            emptyVO.setMinimumReviewCount(MIN_POSITIVE_COUNT);
            emptyVO.setAvailablePositiveCount(availablePositive);
            return List.of(emptyVO);
        }

        // 转换实体为 VO
        int finalAvailable = availablePositive;
        return highlights.stream()
                .map(h -> toVO(h, finalAvailable))
                .collect(Collectors.toList());
    }

    /**
     * 生成或刷新商家亮点。
     *
     * 注意：这里刻意不加 @Transactional —— 失败路径要先插 FAILED 记录再抛异常，
     * 如果整个方法在一个事务里，抛出的异常会把 FAILED 记录一起回滚掉。
     *
     * @param merchantId 商家ID
     * @param force      true 时跳过刷新条件检查，强制重新生成
     * @return 新生成的亮点列表 VO
     */
    public List<MerchantHighlightVO> generateHighlights(Long merchantId, boolean force) {
        AiTraceContext context = traceService == null ? null
                : traceService.startTrace(null, null, null, "MERCHANT_HIGHLIGHT_GENERATION");
        try {
            return generateHighlights(merchantId, force, context);
        } catch (RuntimeException exception) {
            failTrace(context, "HIGHLIGHT_GENERATION_FAILED", exception.getMessage());
            throw exception;
        }
    }

    public List<MerchantHighlightVO> generateHighlights(
            Long merchantId, boolean force, AiTraceContext context
    ) {
        recordRequest(context, merchantId);
        // 1. 校验商家存在
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "MERCHANT_NOT_FOUND",
                    "商家不存在"
            );
        }

        // 2. 加载正面评价（POSITIVE + NEUTRAL，排除 NEGATIVE 和 MIXED）
        List<Review> positiveReviews = loadPositiveReviews(merchantId);
        completeStage(startStage(context, "REVIEW_SOURCE_LOAD", Map.of("merchantId", merchantId,
                        "reviewIds", positiveReviews.stream().map(Review::getId).toList(),
                        "reviewCount", positiveReviews.size())),
                Map.of("reviewCount", positiveReviews.size()), null, null, null, null);

        // 3. 检查是否已有可展示的缓存
        List<MerchantHighlight> existingActive = highlightMapper.selectList(
                new LambdaQueryWrapper<MerchantHighlight>()
                        .eq(MerchantHighlight::getMerchantId, merchantId)
                        .eq(MerchantHighlight::getStatus, "ACTIVE")
        );

        // ==== 正面评论不足：不调模型，返回空状态（验收准则 5） ====
        if (positiveReviews.size() < MIN_POSITIVE_COUNT) {
            // 如果已有旧亮点，将其标记为 OUTDATED
            if (!existingActive.isEmpty() && force) {
                markHighlightsOutdated(existingActive);
            }

            MerchantHighlightVO emptyVO = new MerchantHighlightVO();
            emptyVO.setMerchantId(merchantId);
            emptyVO.setStatus("INSUFFICIENT_DATA");
            emptyVO.setStatusMessage(
                    "正面评论数量不足，至少需要 " + MIN_POSITIVE_COUNT
                            + " 条正面/中性评价才能生成亮点，当前仅有 "
                            + positiveReviews.size() + " 条"
            );
            emptyVO.setMinimumReviewCount(MIN_POSITIVE_COUNT);
            emptyVO.setAvailablePositiveCount(positiveReviews.size());
            List<MerchantHighlightVO> response = List.of(emptyVO);
            finishFallback(context, merchantId, response, positiveReviews, "INSUFFICIENT_DATA");
            return response;
        }

        // ==== 缓存命中：未达刷新条件时返回已有亮点 ====
        if (!force
                && !existingActive.isEmpty()
                && !needsRefresh(existingActive.get(0), positiveReviews.size())) {
            List<MerchantHighlightVO> response = existingActive.stream()
                    .map(h -> toVO(h, positiveReviews.size()))
                    .collect(Collectors.toList());
            finishFallback(context, merchantId, response, positiveReviews, "REUSED_HIGHLIGHTS");
            return response;
        }

        // ==== 调用 AI 服务生成亮点 ====
        int version = nextVersion(merchantId);

        // 加载已有分析结果（关键词、情感），辅助模型更准确挖掘
        Map<Long, ReviewAnalysis> analysisMap = loadAnalysisMap(positiveReviews);

        // 构造评价列表（含分析结果）
        List<Map<String, Object>> reviewPayload = new ArrayList<>();
        for (Review review : positiveReviews) {
            Map<String, Object> item = new HashMap<>();
            item.put("reviewId", review.getId());
            item.put("rating", review.getRating());
            item.put("content", review.getContent());
            item.put("reviewTime",
                    review.getPublishedAt() != null
                            ? review.getPublishedAt().toString()
                            : null);

            // 填入已有分析结果，辅助AI更准确定位亮点
            ReviewAnalysis analysis = analysisMap.get(review.getId());
            if (analysis != null) {
                item.put("keywords", parseJsonArray(analysis.getKeywords()));
                item.put("sentiment", analysis.getSentiment());
            } else {
                item.put("keywords", List.of());
                item.put("sentiment", null);
            }

            reviewPayload.add(item);
        }

        completeStage(startStage(context, "PROMPT_BUILD", Map.of("merchantId", merchantId,
                        "reviewCount", positiveReviews.size(), "promptVersion", "merchant-highlight:v1")),
                Map.of("reviewCount", positiveReviews.size()), null, null, null, "merchant-highlight:v1");

        JsonNode result;
        try {
            result = traceService == null || context == null
                    ? aiClientService.generateMerchantHighlights(merchantId, version, reviewPayload, MIN_POSITIVE_COUNT)
                    : aiClientService.generateMerchantHighlights(merchantId, version, reviewPayload,
                            MIN_POSITIVE_COUNT, context);
        } catch (Exception e) {
            // AI 调用失败时，如果已有旧亮点则继续返回旧的
            if (!existingActive.isEmpty()) {
                List<MerchantHighlightVO> response = existingActive.stream()
                        .map(h -> toVO(h, positiveReviews.size()))
                        .collect(Collectors.toList());
                finishFallback(context, merchantId, response, positiveReviews, "MODEL_CALL_FAILED");
                return response;
            }
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "HIGHLIGHT_GENERATION_FAILED",
                    "亮点挖掘失败，请稍后重试"
            );
        }

        // ==== 解析 AI 返回结果 ====
        String highlightStatus = result.has("highlightStatus")
                ? result.get("highlightStatus").asText()
                : "FAILED";

        completeStage(startStage(context, "OUTPUT_VALIDATION", Map.of("merchantId", merchantId)),
                Map.of("status", highlightStatus), "FASTAPI", textOrNull(result, "modelName"),
                textOrNull(result, "modelVersion"), "merchant-highlight:v1");

        if ("FAILED".equals(highlightStatus)) {
            if (!existingActive.isEmpty()) {
                List<MerchantHighlightVO> response = existingActive.stream()
                        .map(h -> toVO(h, positiveReviews.size()))
                        .collect(Collectors.toList());
                finishFallback(context, merchantId, response, positiveReviews, "MODEL_RETURNED_FAILED");
                return response;
            }
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "HIGHLIGHT_GENERATION_FAILED",
                    "亮点挖掘失败，请稍后重试"
            );
        }

        if ("INSUFFICIENT_DATA".equals(highlightStatus)) {
            MerchantHighlightVO emptyVO = new MerchantHighlightVO();
            emptyVO.setMerchantId(merchantId);
            emptyVO.setStatus("INSUFFICIENT_DATA");
            emptyVO.setStatusMessage("AI 判断当前评论证据不足以生成可靠亮点");
            emptyVO.setMinimumReviewCount(MIN_POSITIVE_COUNT);
            emptyVO.setAvailablePositiveCount(positiveReviews.size());
            List<MerchantHighlightVO> response = List.of(emptyVO);
            finishFallback(context, merchantId, response, positiveReviews, "MODEL_INSUFFICIENT_DATA");
            return response;
        }

        // ==== 成功：标记旧亮点为 OUTDATED ====
        markHighlightsOutdated(existingActive);

        // ==== 落库新亮点 ====
        Set<Long> sentReviewIds = positiveReviews.stream()
                .map(Review::getId)
                .collect(Collectors.toSet());

        JsonNode highlightsArray = result.has("highlights")
                ? result.get("highlights") : null;
        JsonNode evidencesArray = result.has("evidences")
                ? result.get("evidences") : null;

        List<MerchantHighlight> savedHighlights = new ArrayList<>();
        AiRequestTraceStage evidenceStage = startStage(context, "EVIDENCE_SELECTION",
                Map.of("merchantId", merchantId, "sourceReviewIds", sentReviewIds.stream().toList()));

        if (highlightsArray != null && highlightsArray.isArray()) {
            for (JsonNode item : highlightsArray) {
                Long highlightId = insertHighlightRow(
                        merchantId, version,
                        textOrNull(item, "highlightType"),
                        textOrNull(item, "title"),
                        textOrNull(item, "description"),
                        item.has("mentionCount") ? item.get("mentionCount").asInt() : 1,
                        item.has("positiveRatio")
                                ? BigDecimal.valueOf(item.get("positiveRatio").asDouble())
                                : BigDecimal.ONE
                );

                // 落库依据
                if (evidencesArray != null && evidencesArray.isArray()) {
                    String highlightType = textOrNull(item, "highlightType");
                    for (JsonNode ev : evidencesArray) {
                        long reviewId = ev.has("reviewId") ? ev.get("reviewId").asLong() : -1;
                        String evHighlightType = textOrNull(ev, "highlightType");

                        // 防止模型编造 reviewId（验收准则 6）
                        if (!sentReviewIds.contains(reviewId)) {
                            continue;
                        }
                        // 依据类型与亮点类型匹配
                        if (highlightType != null && highlightType.equals(evHighlightType)) {
                            insertEvidenceRow(
                                    highlightId, reviewId,
                                    reviewVersionFor(reviewId, positiveReviews),
                                    textOrNull(ev, "evidenceExcerpt")
                            );
                        }
                    }

                    // 如果模型没有返回匹配的依据，从亮点 reviewIds 中自动补充
                    ensureEvidencesForHighlight(
                            highlightId, item, sentReviewIds,
                            positiveReviews, highlightType);
                }

                savedHighlights.add(highlightMapper.selectById(highlightId));
            }
        }

        if (savedHighlights.isEmpty()) {
            MerchantHighlightVO emptyVO = new MerchantHighlightVO();
            emptyVO.setMerchantId(merchantId);
            emptyVO.setStatus("NONE");
            emptyVO.setStatusMessage("AI 未从当前评论中发现足够亮点");
            emptyVO.setMinimumReviewCount(MIN_POSITIVE_COUNT);
            emptyVO.setAvailablePositiveCount(positiveReviews.size());
            List<MerchantHighlightVO> response = List.of(emptyVO);
            finishFallback(context, merchantId, response, positiveReviews, "NO_HIGHLIGHTS");
            return response;
        }

        List<MerchantHighlightVO> response = savedHighlights.stream()
                .map(h -> toVO(h, positiveReviews.size()))
                .collect(Collectors.toList());
        List<Long> sourceReviewIds = collectSourceReviewIds(evidencesArray, sentReviewIds);
        completeStage(evidenceStage, Map.of("sourceReviewIds", sourceReviewIds,
                        "evidenceCount", sourceReviewIds.size()), null, null, null, null);
        completeStage(startStage(context, "RESULT_PERSIST", Map.of("merchantId", merchantId)),
                Map.of("highlightIds", response.stream().map(MerchantHighlightVO::getHighlightId).toList(),
                        "resultCount", response.size()), null, textOrNull(result, "modelName"),
                textOrNull(result, "modelVersion"), "merchant-highlight:v1");
        finishSuccess(context, merchantId, response, sourceReviewIds,
                textOrNull(result, "modelName"), textOrNull(result, "modelVersion"));
        return response;
    }

    /**
     * 查询亮点依据列表（"查看依据"溯源入口）。
     *
     * @param merchantId   商家ID
     * @param highlightId  亮点ID（可选，默认查询该商家所有活跃亮点的依据）
     * @return 依据 VO 列表
     */
    public List<HighlightEvidenceVO> getHighlightEvidences(
            Long merchantId,
            Long highlightId
    ) {
        List<MerchantHighlightEvidence> evidences;

        if (highlightId != null) {
            // 防跨商家读取
            MerchantHighlight highlight = highlightMapper.selectById(highlightId);
            if (highlight == null || !highlight.getMerchantId().equals(merchantId)) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "HIGHLIGHT_NOT_FOUND",
                        "亮点不存在"
                );
            }
            evidences = evidenceMapper.selectList(
                    new LambdaQueryWrapper<MerchantHighlightEvidence>()
                            .eq(MerchantHighlightEvidence::getHighlightId, highlightId)
            );
        } else {
            // 查该商家所有活跃亮点的依据
            List<MerchantHighlight> activeHighlights = highlightMapper.selectList(
                    new LambdaQueryWrapper<MerchantHighlight>()
                            .eq(MerchantHighlight::getMerchantId, merchantId)
                            .eq(MerchantHighlight::getStatus, "ACTIVE")
            );
            if (activeHighlights.isEmpty()) {
                return List.of();
            }
            List<Long> highlightIds = activeHighlights.stream()
                    .map(MerchantHighlight::getId)
                    .toList();
            evidences = evidenceMapper.selectList(
                    new LambdaQueryWrapper<MerchantHighlightEvidence>()
                            .in(MerchantHighlightEvidence::getHighlightId, highlightIds)
            );
        }

        if (evidences.isEmpty()) {
            return List.of();
        }

        // 批量查原评价，组装展示信息
        List<Long> reviewIds = evidences.stream()
                .map(MerchantHighlightEvidence::getReviewId)
                .distinct()
                .toList();
        Map<Long, Review> reviewMap = reviewMapper.selectByIds(reviewIds)
                .stream()
                .collect(Collectors.toMap(Review::getId, r -> r));

        List<HighlightEvidenceVO> vos = new ArrayList<>();
        for (MerchantHighlightEvidence evidence : evidences) {
            HighlightEvidenceVO vo = new HighlightEvidenceVO();
            vo.setReviewId(evidence.getReviewId());
            vo.setReviewVersion(evidence.getReviewVersion());
            vo.setEvidenceExcerpt(evidence.getEvidenceExcerpt());

            Review review = reviewMap.get(evidence.getReviewId());
            // 原评价被删除/隐藏后不再返回正文（验收准则：评价不可用时标记）
            boolean available = review != null
                    && "PUBLISHED".equals(review.getStatus());
            vo.setReviewAvailable(available);
            if (available) {
                vo.setRating(review.getRating());
                vo.setReviewContent(review.getContent());
                vo.setPublishedAt(review.getPublishedAt());
            }
            vos.add(vo);
        }
        return vos;
    }

    // ==================== 私有方法 ====================

    /**
     * 加载正面评价：POSITIVE + NEUTRAL（验收准则要求基于正面评论），
     * 取最新 N 条，限制提示词长度。
     *
     * 优先使用 AI 分析的情感结果；当分析数据不足时，
     * 回退为评分 >= 4 的评价（保证新导入评价也能参与亮点挖掘）。
     */
    private List<Review> loadPositiveReviews(Long merchantId) {
        // 先查出所有公开评价
        List<Review> allPublic = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getMerchantId, merchantId)
                        .eq(Review::getReviewType, "ORIGINAL")
                        .eq(Review::getStatus, "PUBLISHED")
                        .eq(Review::getModerationStatus, "APPROVED")
                        .orderByDesc(Review::getPublishedAt)
                        .last("LIMIT " + MAX_REVIEWS_FOR_HIGHLIGHT)
        );

        if (allPublic.isEmpty()) {
            return List.of();
        }

        // 获取这些评价的分析结果，过滤出正面/中性
        List<Long> reviewIds = allPublic.stream()
                .map(Review::getId)
                .toList();

        // 查询最新分析结果
        List<ReviewAnalysis> analysisList = reviewAnalysisMapper.selectList(
                new LambdaQueryWrapper<ReviewAnalysis>()
                        .in(ReviewAnalysis::getReviewId, reviewIds)
                        .eq(ReviewAnalysis::getStatus, "SUCCESS")
        );

        // 按 reviewId 取最新分析
        Map<Long, String> sentimentMap = new HashMap<>();
        for (ReviewAnalysis a : analysisList) {
            String existing = sentimentMap.get(a.getReviewId());
            if (existing == null
                    || (a.getAnalysisVersion() != null
                    && a.getAnalysisVersion() > getVersionFromMap(analysisList, a.getReviewId()))) {
                sentimentMap.put(a.getReviewId(), a.getSentiment());
            }
        }

        // 过滤：优先使用AI分析情感，分析缺失时回退为评分>=4
        return allPublic.stream()
                .filter(r -> {
                    String sentiment = sentimentMap.get(r.getId());
                    if (sentiment != null) {
                        // 有分析结果时，严格按情感过滤
                        return "POSITIVE".equals(sentiment) || "NEUTRAL".equals(sentiment);
                    }
                    // 无分析结果时，回退为评分判断：>=4 视为正面
                    return r.getRating() != null && r.getRating().intValue() >= 4;
                })
                .collect(Collectors.toList());
    }

    /**
     * 统计当前可用正面评价数（用于提示）
     */
    private int countPositiveReviews(Long merchantId) {
        List<Review> reviews = loadPositiveReviews(merchantId);
        return reviews.size();
    }

    /** 加载评价分析结果映射 */
    private void recordRequest(AiTraceContext context, Long merchantId) {
        if (context == null || traceService == null) return;
        traceService.updateStructuredConditions(context, Map.of("merchantId", merchantId));
        completeStage(startStage(context, "REQUEST_RECEIVED", Map.of("merchantId", merchantId)),
                Map.of("accepted", true), null, null, null, null);
    }

    private AiRequestTraceStage startStage(AiTraceContext context, String name, Object input) {
        if (context == null || traceService == null) return null;
        try { return traceService.startStage(context, name, input); } catch (Exception ignored) { return null; }
    }

    private void completeStage(AiRequestTraceStage stage, Object output, String provider,
                               String modelName, String modelVersion, String promptVersion) {
        if (stage == null || traceService == null) return;
        try { traceService.completeStage(stage, output, provider, modelName, modelVersion, promptVersion); }
        catch (Exception ignored) { }
    }

    private void finishSuccess(AiTraceContext context, Long merchantId,
                               List<MerchantHighlightVO> response, List<Long> sourceReviewIds,
                               String modelName, String modelVersion) {
        if (context == null || traceService == null) return;
        traceService.completeTrace(context, "SUCCESS", highlightOutput(merchantId, response,
                        sourceReviewIds, false), "FASTAPI", modelName, modelVersion,
                "merchant-highlight:v1");
    }

    private void finishFallback(AiTraceContext context, Long merchantId,
                                List<MerchantHighlightVO> response, List<Review> reviews, String reason) {
        if (context == null || traceService == null) return;
        Map<String, Object> output = highlightOutput(merchantId, response,
                reviews.stream().map(Review::getId).toList(), true);
        output.put("fallbackReason", reason);
        traceService.completeTrace(context, "FALLBACK", output, "RULE_ENGINE", "RULE_ENGINE",
                null, "NOT_APPLICABLE");
    }

    private Map<String, Object> highlightOutput(Long merchantId, List<MerchantHighlightVO> response,
                                                 List<Long> sourceReviewIds, boolean degraded) {
        Map<String, Object> output = new HashMap<>();
        output.put("merchantId", merchantId);
        output.put("highlightIds", response.stream().map(MerchantHighlightVO::getHighlightId).toList());
        output.put("sourceReviewIds", sourceReviewIds);
        output.put("resultCount", response.size());
        output.put("status", response.isEmpty() ? "NONE" : response.get(0).getStatus());
        output.put("degraded", degraded);
        return output;
    }

    private List<Long> collectSourceReviewIds(JsonNode evidences, Set<Long> allowed) {
        if (evidences == null || !evidences.isArray()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (JsonNode evidence : evidences) {
            long reviewId = evidence.path("reviewId").asLong(-1);
            if (allowed.contains(reviewId) && !ids.contains(reviewId)) ids.add(reviewId);
        }
        return ids;
    }

    private void failTrace(AiTraceContext context, String code, String message) {
        if (context != null && traceService != null) traceService.failTraceSafely(context, code, message);
    }

    private Map<Long, ReviewAnalysis> loadAnalysisMap(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = reviews.stream().map(Review::getId).toList();
        List<ReviewAnalysis> list = reviewAnalysisMapper.selectList(
                new LambdaQueryWrapper<ReviewAnalysis>()
                        .in(ReviewAnalysis::getReviewId, ids)
                        .eq(ReviewAnalysis::getStatus, "SUCCESS")
        );

        // 每个 review 取最新版本
        Map<Long, ReviewAnalysis> map = new HashMap<>();
        for (ReviewAnalysis a : list) {
            ReviewAnalysis existing = map.get(a.getReviewId());
            if (existing == null
                    || (a.getAnalysisVersion() != null
                    && existing.getAnalysisVersion() != null
                    && a.getAnalysisVersion() > existing.getAnalysisVersion())) {
                map.put(a.getReviewId(), a);
            }
        }
        return map;
    }

    /** 下一个版本号 */
    private int nextVersion(Long merchantId) {
        MerchantHighlight latest = highlightMapper.selectOne(
                new LambdaQueryWrapper<MerchantHighlight>()
                        .eq(MerchantHighlight::getMerchantId, merchantId)
                        .orderByDesc(MerchantHighlight::getVersion)
                        .last("LIMIT 1")
        );
        return latest == null ? 1 : latest.getVersion() + 1;
    }

    /** 刷新条件检查 */
    private boolean needsRefresh(
            MerchantHighlight latest,
            int currentPositiveCount
    ) {
        // 无现有亮点时始终需要生成
        if (latest == null) {
            return true;
        }
        // 过期检查
        if (latest.getGeneratedAt() != null) {
            Duration age = Duration.between(
                    latest.getGeneratedAt(), OffsetDateTime.now());
            if (age.toDays() >= REFRESH_MAX_AGE_DAYS) {
                return true;
            }
        }
        // 新评论达到阈值（通过与生成时的 mentionCount 比较来近似判断）
        // 实际做法：如果当前正面评价数明显多于上次生成时的基数，就允许刷新
        // 这里用简单的过期判断作为主要刷新条件
        return false;
    }

    /** 将旧亮点标记为 OUTDATED */
    private void markHighlightsOutdated(List<MerchantHighlight> highlights) {
        for (MerchantHighlight h : highlights) {
            if ("ACTIVE".equals(h.getStatus())) {
                h.setStatus("OUTDATED");
                highlightMapper.updateById(h);
            }
        }
    }

    /** 插入亮点主记录 */
    private Long insertHighlightRow(
            Long merchantId, int version,
            String highlightType, String title, String description,
            int mentionCount, BigDecimal positiveRatio
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO merchant_highlights (
                    merchant_id, version, highlight_type,
                    title, description, mention_count,
                    positive_ratio, status, generated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                merchantId, version, highlightType,
                title, description, mentionCount,
                positiveRatio
        );
    }

    /** 插入依据记录 */
    private void insertEvidenceRow(
            Long highlightId, Long reviewId,
            Integer reviewVersion, String evidenceExcerpt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO merchant_highlight_evidences (
                    highlight_id, review_id, review_version,
                    evidence_excerpt, created_at
                )
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (highlight_id, review_id)
                DO NOTHING
                """,
                highlightId,
                reviewId,
                reviewVersion != null ? reviewVersion : 1,
                evidenceExcerpt
        );
    }

    /**
     * 确保亮点至少有一条依据：从亮点 reviewIds 中取第一条评价的原文摘录
     * 作为兜底依据，保证验收准则 2 的可追溯性。
     */
    private void ensureEvidencesForHighlight(
            Long highlightId,
            JsonNode highlightItem,
            Set<Long> sentReviewIds,
            List<Review> positiveReviews,
            String highlightType
    ) {
        // 检查是否已有依据
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_highlight_evidences WHERE highlight_id = ?",
                Long.class, highlightId
        );
        if (count != null && count > 0) {
            return; // 已有依据，无需补充
        }

        // 从亮点 reviewIds 中取第一条真实评价作为兜底
        if (highlightItem.has("reviewIds") && highlightItem.get("reviewIds").isArray()) {
            for (JsonNode rid : highlightItem.get("reviewIds")) {
                long reviewId = rid.asLong();
                if (!sentReviewIds.contains(reviewId)) {
                    continue;
                }
                Review review = positiveReviews.stream()
                        .filter(r -> r.getId().equals(reviewId))
                        .findFirst()
                        .orElse(null);
                if (review != null) {
                    String excerpt = review.getContent();
                    if (excerpt != null && excerpt.length() > 100) {
                        excerpt = excerpt.substring(0, 100);
                    }
                    insertEvidenceRow(
                            highlightId, reviewId,
                            review.getCurrentVersion(),
                            excerpt
                    );
                    break; // 一条兜底依据就够了
                }
            }
        }
    }

    /** 解析 JSONB 中的关键词数组 */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode item : node) {
                    result.add(item.asText());
                }
                return result;
            }
        } catch (Exception ignored) {
            // 解析失败返回空
        }
        return List.of();
    }

    /** 实体 → 展示 VO */
    private MerchantHighlightVO toVO(MerchantHighlight highlight, int availablePositive) {
        MerchantHighlightVO vo = new MerchantHighlightVO();
        vo.setHighlightId(highlight.getId());
        vo.setMerchantId(highlight.getMerchantId());
        vo.setHighlightType(highlight.getHighlightType());
        vo.setTitle(highlight.getTitle());
        vo.setDescription(highlight.getDescription());
        vo.setMentionCount(highlight.getMentionCount());
        vo.setPositiveRatio(highlight.getPositiveRatio());
        vo.setVersion(highlight.getVersion());
        vo.setStatus(highlight.getStatus());
        vo.setGeneratedAt(highlight.getGeneratedAt());
        vo.setMinimumReviewCount(MIN_POSITIVE_COUNT);
        vo.setAvailablePositiveCount(availablePositive);

        // 状态消息
        switch (highlight.getStatus()) {
            case "ACTIVE" -> vo.setStatusMessage("当前亮点基于 "
                    + highlight.getMentionCount() + " 条正面评价生成");
            case "OUTDATED" -> vo.setStatusMessage("该亮点已过期，请刷新");
            case "DISABLED" -> vo.setStatusMessage("该亮点已禁用");
        }

        return vo;
    }

    /** 从分析列表中获取 reviewId 对应的最大版本号 */
    private int getVersionFromMap(List<ReviewAnalysis> list, Long reviewId) {
        return list.stream()
                .filter(a -> a.getReviewId().equals(reviewId))
                .mapToInt(a -> a.getAnalysisVersion() != null ? a.getAnalysisVersion() : 0)
                .max()
                .orElse(0);
    }

    /** 获取评价版本号 */
    private Integer reviewVersionFor(Long reviewId, List<Review> reviews) {
        return reviews.stream()
                .filter(r -> r.getId().equals(reviewId))
                .findFirst()
                .map(Review::getCurrentVersion)
                .orElse(1);
    }

    private String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText()
                : null;
    }
}
