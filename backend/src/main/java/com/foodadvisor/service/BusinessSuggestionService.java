package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.suggestion.BusinessSuggestionEvidenceVO;
import com.foodadvisor.dto.suggestion.BusinessSuggestionVO;
import com.foodadvisor.entity.*;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.BusinessSuggestionEvidenceMapper;
import com.foodadvisor.mapper.BusinessSuggestionMapper;
import com.foodadvisor.mapper.CompetitorComparisonMapper;
import com.foodadvisor.mapper.MerchantHighlightMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.MerchantReputationStatisticsMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.trace.AiTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 经营改进建议生成服务（EPIC-02 Story 8）
 *
 * 结合近期情感趋势、差评归因、商家亮点和竞品对比结果，
 * 为商家生成阶段性的经营改进建议。每项建议明确说明数据依据，
 * 区分短期/长期措施，数据不足时降低结论确定性。
 *
 * 验收准则对齐：
 * - AC-1: 每项建议至少关联一个口碑趋势、差评类别、商家亮点或竞品对比数据
 * - AC-2: 每项建议展示对应指标、数量、占比或原评论依据
 * - AC-3: 每项建议至少包含问题对象、改进措施和适用时间范围
 * - AC-4: 建议能够标记为短期或长期
 * - AC-5: 数据量低于配置阈值时显示依据有限，并降低结论确定性
 * - AC-6: 预置数据中不存在的问题不会被作为主要改进建议
 */
@Service
public class BusinessSuggestionService {

    private static final Logger log =
            LoggerFactory.getLogger(BusinessSuggestionService.class);

    // ---- 数据充足性阈值 ----
    /** 生成建议所需的最少有效评价数 */
    private static final int MIN_REVIEW_COUNT = 3;
    /** 生成建议所需的最少口碑统计周期数 */
    private static final int MIN_REPUTATION_PERIODS = 1;
    /** 建议缓存有效期（天） */
    private static final int REFRESH_MAX_AGE_DAYS = 7;
    /** 最近N天的数据窗口 */
    private static final int RECENT_DATA_DAYS = 90;

    private final BusinessSuggestionMapper suggestionMapper;
    private final BusinessSuggestionEvidenceMapper evidenceMapper;
    private final MerchantMapper merchantMapper;
    private final MerchantReputationStatisticsMapper reputationMapper;
    private final ReviewMapper reviewMapper;
    private final MerchantHighlightMapper highlightMapper;
    private final CompetitorComparisonMapper competitorComparisonMapper;
    private final AIClientService aiClientService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiRequestTraceService traceService;

    public BusinessSuggestionService(
            BusinessSuggestionMapper suggestionMapper,
            BusinessSuggestionEvidenceMapper evidenceMapper,
            MerchantMapper merchantMapper,
            MerchantReputationStatisticsMapper reputationMapper,
            ReviewMapper reviewMapper,
            MerchantHighlightMapper highlightMapper,
            CompetitorComparisonMapper competitorComparisonMapper,
            AIClientService aiClientService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this(suggestionMapper, evidenceMapper, merchantMapper,
                reputationMapper, reviewMapper, highlightMapper,
                competitorComparisonMapper, aiClientService,
                jdbcTemplate, objectMapper, null);
    }

    @Autowired
    public BusinessSuggestionService(
            BusinessSuggestionMapper suggestionMapper,
            BusinessSuggestionEvidenceMapper evidenceMapper,
            MerchantMapper merchantMapper,
            MerchantReputationStatisticsMapper reputationMapper,
            ReviewMapper reviewMapper,
            MerchantHighlightMapper highlightMapper,
            CompetitorComparisonMapper competitorComparisonMapper,
            AIClientService aiClientService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AiRequestTraceService traceService
    ) {
        this.suggestionMapper = suggestionMapper;
        this.evidenceMapper = evidenceMapper;
        this.merchantMapper = merchantMapper;
        this.reputationMapper = reputationMapper;
        this.reviewMapper = reviewMapper;
        this.highlightMapper = highlightMapper;
        this.competitorComparisonMapper = competitorComparisonMapper;
        this.aiClientService = aiClientService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.traceService = traceService;
    }

    // ==================== 公开方法：只读查询 ====================

    /**
     * 获取商家的活跃建议列表（只读缓存）。
     *
     * @param merchantId 商家ID
     * @return 建议VO列表，无数据时返回空状态
     */
    public List<BusinessSuggestionVO> getDisplaySuggestions(Long merchantId) {
        List<BusinessSuggestion> suggestions =
                suggestionMapper.selectActiveByMerchantId(merchantId);

        if (suggestions.isEmpty()) {
            // 返回空状态
            BusinessSuggestionVO emptyVO = new BusinessSuggestionVO();
            emptyVO.setMerchantId(merchantId);
            emptyVO.setStatus("NONE");
            emptyVO.setGenerationStatus("NONE");

            // 检查各数据源可用性
            int reviewCount = countPublishedReviews(merchantId);
            emptyVO.setDataSources(buildDataSourceStatuses(merchantId, reviewCount));

            if (reviewCount < MIN_REVIEW_COUNT) {
                emptyVO.setGenerationMessage(
                        "评价数量不足（当前 " + reviewCount + " 条，需要至少 "
                                + MIN_REVIEW_COUNT + " 条），无法生成可靠的经营建议");
            } else {
                emptyVO.setGenerationMessage("经营改进建议尚未生成，请点击刷新按钮触发生成");
            }
            return List.of(emptyVO);
        }

        return suggestions.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 查询建议依据列表。
     *
     * @param merchantId   商家ID
     * @param suggestionId 建议ID（可选，不传则返回所有活跃建议的依据）
     * @return 依据VO列表
     */
    public List<BusinessSuggestionEvidenceVO> getEvidences(
            Long merchantId, Long suggestionId) {

        List<BusinessSuggestionEvidence> evidences;
        if (suggestionId != null) {
            // 防跨商家读取
            BusinessSuggestion suggestion = suggestionMapper.selectById(suggestionId);
            if (suggestion == null || !suggestion.getMerchantId().equals(merchantId)) {
                throw new ApiException(HttpStatus.NOT_FOUND,
                        "SUGGESTION_NOT_FOUND", "建议不存在");
            }
            evidences = evidenceMapper.selectBySuggestionId(suggestionId);
        } else {
            List<BusinessSuggestion> active =
                    suggestionMapper.selectActiveByMerchantId(merchantId);
            if (active.isEmpty()) return List.of();
            List<Long> ids = active.stream()
                    .map(BusinessSuggestion::getId).toList();
            evidences = evidenceMapper.selectBySuggestionIds(ids);
        }

        if (evidences.isEmpty()) return List.of();

        // 批量查询原始评价
        List<Long> reviewIds = evidences.stream()
                .map(BusinessSuggestionEvidence::getReviewId)
                .filter(Objects::nonNull)
                .distinct().toList();
        final Map<Long, Review> reviewMap;
        if (!reviewIds.isEmpty()) {
            reviewMap = reviewMapper.selectByIds(reviewIds).stream()
                    .collect(Collectors.toMap(Review::getId, r -> r));
        } else {
            reviewMap = Collections.emptyMap();
        }

        return evidences.stream()
                .map(e -> toEvidenceVO(e, reviewMap))
                .collect(Collectors.toList());
    }

    // ==================== 公开方法：生成/刷新 ====================

    /**
     * 生成或刷新经营改进建议。
     */
    public List<BusinessSuggestionVO> generateSuggestions(Long merchantId, boolean force) {
        AiTraceContext context = traceService == null ? null
                : traceService.startTrace(null, null, null,
                "BUSINESS_SUGGESTION_GENERATION");
        try {
            return generateSuggestions(merchantId, force, context);
        } catch (RuntimeException ex) {
            if (context != null) {
                traceService.failTraceSafely(context,
                        "SUGGESTION_GENERATION_FAILED", ex.getMessage());
            }
            throw ex;
        }
    }

    /**
     * 生成或刷新经营改进建议（带AI追踪上下文）。
     */
    public List<BusinessSuggestionVO> generateSuggestions(
            Long merchantId, boolean force, AiTraceContext context) {

        // 1. 校验商家存在
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "MERCHANT_NOT_FOUND", "商家不存在");
        }

        // 2. 检查缓存是否有效
        List<BusinessSuggestion> existingActive = suggestionMapper.selectList(
                new LambdaQueryWrapper<BusinessSuggestion>()
                        .eq(BusinessSuggestion::getMerchantId, merchantId)
                        .eq(BusinessSuggestion::getStatus, "ACTIVE"));
        if (!force && !existingActive.isEmpty() && !needsRefresh(existingActive.get(0))) {
            return existingActive.stream()
                    .map(this::toVO)
                    .collect(Collectors.toList());
        }

        // 3. 收集各数据源
        int reviewCount = countPublishedReviews(merchantId);
        DataCollectionResult dataResult = collectDataSource(merchantId, reviewCount);

        // 4. 数据不足判断
        if (dataResult.insufficientData) {
            if (!existingActive.isEmpty() && !force) {
                return existingActive.stream().map(this::toVO).collect(Collectors.toList());
            }
            // 标记旧建议为过期
            markSuggestionsOutdated(existingActive);

            BusinessSuggestionVO emptyVO = new BusinessSuggestionVO();
            emptyVO.setMerchantId(merchantId);
            emptyVO.setStatus("INSUFFICIENT_DATA");
            emptyVO.setGenerationStatus("INSUFFICIENT_DATA");
            emptyVO.setGenerationMessage(
                    "数据量不足，无法生成可靠的经营改进建议。"
                            + "当前有效评价 " + reviewCount + " 条（至少需要 "
                            + MIN_REVIEW_COUNT + " 条）");
            emptyVO.setDataSources(dataResult.statuses);
            emptyVO.setConfidence("LOW");
            return List.of(emptyVO);
        }

        // 5. 调用AI服务生成建议
        int version = nextVersion(merchantId);
        JsonNode aiResult;
        try {
            aiResult = context == null
                    ? aiClientService.generateBusinessSuggestions(
                            merchantId, version, dataResult.toAiPayload())
                    : aiClientService.generateBusinessSuggestions(
                            merchantId, version, dataResult.toAiPayload(), context);
        } catch (Exception e) {
            log.error("经营建议生成AI调用失败 merchantId={}: {}", merchantId, e.getMessage());
            if (!existingActive.isEmpty()) {
                return existingActive.stream().map(this::toVO).collect(Collectors.toList());
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "SUGGESTION_GENERATION_FAILED",
                    "经营建议生成失败，请稍后重试");
        }

        // 6. 解析并持久化
        String aiStatus = aiResult.has("status")
                ? aiResult.get("status").asText() : "FAILED";

        if ("FAILED".equals(aiStatus)) {
            if (!existingActive.isEmpty()) {
                return existingActive.stream().map(this::toVO).collect(Collectors.toList());
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "SUGGESTION_GENERATION_FAILED",
                    "AI 服务返回失败状态");
        }

        if ("INSUFFICIENT_DATA".equals(aiStatus)) {
            markSuggestionsOutdated(existingActive);
            BusinessSuggestionVO emptyVO = new BusinessSuggestionVO();
            emptyVO.setMerchantId(merchantId);
            emptyVO.setStatus("INSUFFICIENT_DATA");
            emptyVO.setGenerationStatus("INSUFFICIENT_DATA");
            emptyVO.setGenerationMessage("AI 判断当前数据不足以生成可靠建议");
            emptyVO.setConfidence("LOW");
            emptyVO.setDataSources(dataResult.statuses);
            return List.of(emptyVO);
        }

        // 标记旧建议为过期
        markSuggestionsOutdated(existingActive);

        // 持久化新建议
        JsonNode suggestionsArray = aiResult.has("suggestions")
                ? aiResult.get("suggestions") : null;
        if (suggestionsArray == null || !suggestionsArray.isArray()
                || suggestionsArray.isEmpty()) {
            BusinessSuggestionVO emptyVO = new BusinessSuggestionVO();
            emptyVO.setMerchantId(merchantId);
            emptyVO.setStatus("NONE");
            emptyVO.setGenerationStatus("NONE");
            emptyVO.setGenerationMessage("AI 未从当前数据中发现明确的改进建议");
            emptyVO.setDataSources(dataResult.statuses);
            return List.of(emptyVO);
        }

        List<BusinessSuggestion> saved = new ArrayList<>();
        for (JsonNode item : suggestionsArray) {
            Long savedId = insertSuggestion(merchantId, version, item);

            // 插入依据
            JsonNode evidencesArr = item.has("evidences")
                    ? item.get("evidences") : null;
            if (evidencesArr != null && evidencesArr.isArray()) {
                for (JsonNode ev : evidencesArr) {
                    insertEvidence(savedId, ev);
                }
            }
            // 如果没有依据，自动从数据源摘录兜底依据
            ensureSuggestionEvidence(savedId, item, dataResult);

            saved.add(suggestionMapper.selectById(savedId));
        }

        if (saved.isEmpty()) {
            BusinessSuggestionVO emptyVO = new BusinessSuggestionVO();
            emptyVO.setMerchantId(merchantId);
            emptyVO.setStatus("NONE");
            emptyVO.setGenerationStatus("NONE");
            emptyVO.setGenerationMessage("建议生成未产生有效结果");
            emptyVO.setDataSources(dataResult.statuses);
            return List.of(emptyVO);
        }

        String traceId = context != null ? context.traceId() : null;
        return saved.stream()
                .map(s -> {
                    BusinessSuggestionVO vo = toVO(s);
                    vo.setTraceId(traceId);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    // ==================== 数据收集 ====================

    /**
     * 收集所有数据源的信息。
     */
    private DataCollectionResult collectDataSource(Long merchantId, int reviewCount) {
        DataCollectionResult result = new DataCollectionResult();
        List<BusinessSuggestionVO.DataSourceStatus> statuses = new ArrayList<>();

        // ---- 1. 口碑趋势数据 ----
        List<MerchantReputationStatistics> reputationData =
                reputationMapper.findByMerchantAndPeriod(
                        merchantId, "MONTH",
                        LocalDate.now().minusDays(RECENT_DATA_DAYS),
                        LocalDate.now());
        boolean reputationAvailable = reputationData.size() >= MIN_REPUTATION_PERIODS;
        statuses.add(BusinessSuggestionVO.DataSourceStatus.builder()
                .sourceType("REPUTATION_TREND")
                .available(reputationAvailable)
                .dataCount(reputationData.size())
                .minimumRequired(MIN_REPUTATION_PERIODS)
                .message(reputationAvailable ? "口碑趋势数据充足"
                        : "口碑趋势数据不足（需要至少" + MIN_REPUTATION_PERIODS + "个统计周期）")
                .build());
        result.reputationData = reputationData;

        // ---- 2. 差评归因数据 ----
        List<Map<String, Object>> issueStats = queryIssueStats(merchantId);
        boolean issuesAvailable = !issueStats.isEmpty();
        statuses.add(BusinessSuggestionVO.DataSourceStatus.builder()
                .sourceType("NEGATIVE_ISSUE")
                .available(issuesAvailable)
                .dataCount(issueStats.size())
                .minimumRequired(1)
                .message(issuesAvailable ? "差评归因数据可用"
                        : "暂无差评归因数据")
                .build());
        result.issueStats = issueStats;

        // ---- 3. 商家亮点数据 ----
        List<MerchantHighlight> highlights = highlightMapper.selectList(
                new LambdaQueryWrapper<MerchantHighlight>()
                        .eq(MerchantHighlight::getMerchantId, merchantId)
                        .eq(MerchantHighlight::getStatus, "ACTIVE"));
        boolean highlightsAvailable = !highlights.isEmpty();
        statuses.add(BusinessSuggestionVO.DataSourceStatus.builder()
                .sourceType("HIGHLIGHT")
                .available(highlightsAvailable)
                .dataCount(highlights.size())
                .minimumRequired(1)
                .message(highlightsAvailable ? "商家亮点数据可用"
                        : "暂无商家亮点数据")
                .build());
        result.highlights = highlights;

        // ---- 4. 竞品对比数据 ----
        Merchant merchant = merchantMapper.selectById(merchantId);
        List<Map<String, Object>> competitorData = Collections.emptyList();
        boolean competitorAvailable = false;
        if (merchant != null) {
            competitorData = competitorComparisonMapper.findNearbyCompetitors(
                    merchantId, merchant.getRegionCode(),
                    merchant.getCategory(), merchant.getCuisine(), 5);
            competitorAvailable = !competitorData.isEmpty();
        }
        statuses.add(BusinessSuggestionVO.DataSourceStatus.builder()
                .sourceType("COMPETITOR")
                .available(competitorAvailable)
                .dataCount(competitorData.size())
                .minimumRequired(1)
                .message(competitorAvailable ? "竞品对比数据可用"
                        : "周边暂无可用竞品数据")
                .build());
        result.competitorData = competitorData;

        result.statuses = statuses;

        // 综合判断：至少需要评价达标 + 任一分析数据源可用
        boolean hasAnalysisData = reputationAvailable || issuesAvailable
                || highlightsAvailable || competitorAvailable;
        result.insufficientData = reviewCount < MIN_REVIEW_COUNT || !hasAnalysisData;
        result.reviewCount = reviewCount;

        return result;
    }

    /**
     * 查询商家差评归因统计。
     */
    private List<Map<String, Object>> queryIssueStats(Long merchantId) {
        String sql = """
                SELECT ric.code AS category_code,
                       ric.name AS category_name,
                       COUNT(DISTINCT rir.review_id) AS review_count,
                       ROUND(
                           COUNT(DISTINCT rir.review_id)::numeric
                           / NULLIF(
                               (SELECT COUNT(*) FROM reviews
                                WHERE merchant_id = ? AND status = 'PUBLISHED'
                                  AND review_type = 'ORIGINAL'), 0
                           ) * 100, 1
                       ) AS percentage
                FROM review_issue_relations rir
                JOIN review_issue_categories ric ON ric.id = rir.issue_category_id
                JOIN reviews r ON r.id = rir.review_id
                WHERE r.merchant_id = ?
                  AND r.status = 'PUBLISHED'
                GROUP BY ric.code, ric.name
                ORDER BY review_count DESC
                """;
        return jdbcTemplate.queryForList(sql, merchantId, merchantId);
    }

    // ==================== 持久化辅助方法 ====================

    private Long insertSuggestion(Long merchantId, int version, JsonNode item) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO business_suggestions (
                    merchant_id, version, title, description,
                    category, priority, timeframe, expected_effect,
                    data_basis_type, data_basis_summary,
                    metric_name, metric_value, confidence,
                    status, generated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class,
                merchantId,
                version,
                textOrNull(item, "title"),
                textOrNull(item, "description"),
                textOrNull(item, "category"),
                textOrDefault(item, "priority", "MEDIUM"),
                textOrDefault(item, "timeframe", "SHORT_TERM"),
                textOrNull(item, "expectedEffect"),
                textOrNull(item, "dataBasisType"),
                textOrNull(item, "dataBasisSummary"),
                textOrNull(item, "metricName"),
                textOrNull(item, "metricValue"),
                textOrDefault(item, "confidence", "MEDIUM")
        );
    }

    private void insertEvidence(Long suggestionId, JsonNode ev) {
        String metricSnapshot = null;
        if (ev.has("metricSnapshot") && !ev.get("metricSnapshot").isNull()) {
            try {
                metricSnapshot = objectMapper.writeValueAsString(
                        ev.get("metricSnapshot"));
            } catch (JsonProcessingException ignored) {
            }
        }
        jdbcTemplate.update("""
                INSERT INTO business_suggestion_evidences (
                    suggestion_id, source_type, source_id,
                    review_id, metric_snapshot, evidence_excerpt, created_at
                ) VALUES (?, ?, ?, ?, ?::jsonb, ?, CURRENT_TIMESTAMP)
                """,
                suggestionId,
                textOrDefault(ev, "sourceType", "REVIEW"),
                ev.has("sourceId") && !ev.get("sourceId").isNull()
                        ? ev.get("sourceId").asLong() : null,
                ev.has("reviewId") && !ev.get("reviewId").isNull()
                        ? ev.get("reviewId").asLong() : null,
                metricSnapshot,
                textOrNull(ev, "evidenceExcerpt")
        );
    }

    /**
     * 确保建议至少有一条依据（兜底）。
     */
    private void ensureSuggestionEvidence(Long suggestionId, JsonNode item,
                                          DataCollectionResult data) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM business_suggestion_evidences WHERE suggestion_id = ?",
                Long.class, suggestionId);
        if (count != null && count > 0) return;

        String category = textOrNull(item, "category");
        // 根据类别从不同数据源自动生成兜底依据
        if ("NEGATIVE_ISSUE".equals(category) && !data.issueStats.isEmpty()) {
            Map<String, Object> firstIssue = data.issueStats.get(0);
            jdbcTemplate.update("""
                    INSERT INTO business_suggestion_evidences (
                        suggestion_id, source_type, source_id,
                        metric_snapshot, evidence_excerpt, created_at
                    ) VALUES (?, 'NEGATIVE_ISSUE', NULL,
                        ?::jsonb, ?, CURRENT_TIMESTAMP)
                    """,
                    suggestionId,
                    buildSimpleMetricSnapshot(firstIssue),
                    "差评类别「" + firstIssue.get("category_name")
                            + "」涉及 " + firstIssue.get("review_count") + " 条评价（占比 "
                            + firstIssue.get("percentage") + "%）"
            );
        } else if ("REPUTATION_TREND".equals(category)
                && !data.reputationData.isEmpty()) {
            MerchantReputationStatistics latest = data.reputationData.get(
                    data.reputationData.size() - 1);
            jdbcTemplate.update("""
                    INSERT INTO business_suggestion_evidences (
                        suggestion_id, source_type, source_id,
                        metric_snapshot, evidence_excerpt, created_at
                    ) VALUES (?, 'REPUTATION_TREND', ?,
                        ?::jsonb, ?, CURRENT_TIMESTAMP)
                    """,
                    suggestionId, latest.getId(),
                    buildReputationSnapshot(latest),
                    "最近统计周期（" + latest.getPeriodStart() + " 至 "
                            + latest.getPeriodEnd() + "）平均评分 "
                            + latest.getAverageRating()
                            + "，负面评价占比 "
                            + (latest.getNegativeRatio() != null
                            ? latest.getNegativeRatio().multiply(
                                    BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                            + "%" : "N/A")
            );
        }
    }

    private String buildSimpleMetricSnapshot(Map<String, Object> issueStat) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("metricName", "差评类别占比");
            snapshot.put("categoryName", issueStat.get("category_name"));
            snapshot.put("reviewCount", issueStat.get("review_count"));
            snapshot.put("percentage", issueStat.get("percentage"));
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String buildReputationSnapshot(MerchantReputationStatistics stats) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("periodType", stats.getPeriodType());
            snapshot.put("periodStart", stats.getPeriodStart().toString());
            snapshot.put("periodEnd", stats.getPeriodEnd().toString());
            snapshot.put("averageRating", stats.getAverageRating());
            snapshot.put("positiveRatio", stats.getPositiveRatio());
            snapshot.put("negativeRatio", stats.getNegativeRatio());
            snapshot.put("totalReviewCount", stats.getTotalReviewCount());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    // ==================== 转换方法 ====================

    private BusinessSuggestionVO toVO(BusinessSuggestion entity) {
        return BusinessSuggestionVO.builder()
                .suggestionId(entity.getId())
                .merchantId(entity.getMerchantId())
                .version(entity.getVersion())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .priority(entity.getPriority())
                .timeframe(entity.getTimeframe())
                .expectedEffect(entity.getExpectedEffect())
                .dataBasisType(entity.getDataBasisType())
                .dataBasisSummary(entity.getDataBasisSummary())
                .metricName(entity.getMetricName())
                .metricValue(entity.getMetricValue())
                .confidence(entity.getConfidence())
                .status(entity.getStatus())
                .generatedAt(entity.getGeneratedAt())
                .statusMessage(buildStatusMessage(entity))
                .build();
    }

    private BusinessSuggestionEvidenceVO toEvidenceVO(
            BusinessSuggestionEvidence entity,
            Map<Long, Review> reviewMap) {
        BusinessSuggestionEvidenceVO.BusinessSuggestionEvidenceVOBuilder builder =
                BusinessSuggestionEvidenceVO.builder()
                        .evidenceId(entity.getId())
                        .suggestionId(entity.getSuggestionId())
                        .sourceType(entity.getSourceType())
                        .sourceId(entity.getSourceId())
                        .reviewId(entity.getReviewId())
                        .evidenceExcerpt(entity.getEvidenceExcerpt());

        // 解析 metric_snapshot JSONB
        if (entity.getMetricSnapshot() != null
                && !entity.getMetricSnapshot().isBlank()) {
            try {
                JsonNode snapshot = objectMapper.readTree(
                        entity.getMetricSnapshot());
                builder.metricName(textOrNull(snapshot, "metricName"))
                        .currentValue(textOrNull(snapshot, "currentValue"))
                        .previousValue(textOrNull(snapshot, "previousValue"))
                        .changeDirection(textOrNull(snapshot, "changeDirection"))
                        .periodType(textOrNull(snapshot, "periodType"))
                        .periodStart(textOrNull(snapshot, "periodStart"))
                        .periodEnd(textOrNull(snapshot, "periodEnd"));
            } catch (JsonProcessingException ignored) {
            }
        }

        // 原始评论信息
        if (entity.getReviewId() != null) {
            Review review = reviewMap.get(entity.getReviewId());
            boolean available = review != null
                    && "PUBLISHED".equals(review.getStatus());
            builder.reviewAvailable(available);
            if (available) {
                builder.rating(review.getRating() != null
                                ? review.getRating().intValue() : null)
                        .reviewContent(review.getContent())
                        .publishedAt(review.getPublishedAt());
            } else {
                builder.unavailableReason(
                        review == null ? "SOURCE_DELETED" : "SOURCE_HIDDEN");
            }
        }

        return builder.build();
    }

    private String buildStatusMessage(BusinessSuggestion entity) {
        return switch (entity.getStatus()) {
            case "ACTIVE" -> "基于 " + entity.getDataBasisType()
                    + " 数据生成（置信度: " + entity.getConfidence() + "）";
            case "OUTDATED" -> "该建议已过期，请刷新";
            case "DISABLED" -> "该建议已禁用";
            default -> "";
        };
    }

    private List<BusinessSuggestionVO.DataSourceStatus> buildDataSourceStatuses(
            Long merchantId, int reviewCount) {
        // 返回简化的数据源状态
        DataCollectionResult result = collectDataSource(merchantId, reviewCount);
        return result.statuses;
    }

    // ==================== 工具方法 ====================

    private int countPublishedReviews(Long merchantId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE merchant_id = ? "
                        + "AND status = 'PUBLISHED' AND review_type = 'ORIGINAL'",
                Long.class, merchantId);
        return count != null ? count.intValue() : 0;
    }

    private int nextVersion(Long merchantId) {
        return suggestionMapper.selectMaxVersion(merchantId) + 1;
    }

    private boolean needsRefresh(BusinessSuggestion latest) {
        if (latest.getGeneratedAt() == null) return true;
        long days = Duration.between(latest.getGeneratedAt(),
                OffsetDateTime.now()).toDays();
        return days >= REFRESH_MAX_AGE_DAYS;
    }

    private void markSuggestionsOutdated(List<BusinessSuggestion> suggestions) {
        for (BusinessSuggestion s : suggestions) {
            if ("ACTIVE".equals(s.getStatus())) {
                s.setStatus("OUTDATED");
                suggestionMapper.updateById(s);
            }
        }
    }

    private String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText() : null;
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        String val = textOrNull(node, field);
        return val != null ? val : defaultValue;
    }

    // ==================== 内部数据类 ====================

    /**
     * 数据收集结果，聚合四个数据源的信息。
     */
    private static class DataCollectionResult {
        boolean insufficientData;
        int reviewCount;
        List<MerchantReputationStatistics> reputationData = Collections.emptyList();
        List<Map<String, Object>> issueStats = Collections.emptyList();
        List<MerchantHighlight> highlights = Collections.emptyList();
        List<Map<String, Object>> competitorData = Collections.emptyList();
        List<BusinessSuggestionVO.DataSourceStatus> statuses = Collections.emptyList();

        /**
         * 构建发送给 AI 服务的数据载体。
         */
        Map<String, Object> toAiPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reviewCount", reviewCount);
            payload.put("minimumReviewCount", MIN_REVIEW_COUNT);

            // 口碑趋势
            if (!reputationData.isEmpty()) {
                List<Map<String, Object>> trends = new ArrayList<>();
                for (MerchantReputationStatistics s : reputationData) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("periodType", s.getPeriodType());
                    point.put("periodStart", s.getPeriodStart().toString());
                    point.put("periodEnd", s.getPeriodEnd().toString());
                    point.put("averageRating", s.getAverageRating());
                    point.put("positiveRatio", s.getPositiveRatio());
                    point.put("negativeRatio", s.getNegativeRatio());
                    point.put("totalReviewCount", s.getTotalReviewCount());
                    trends.add(point);
                }
                payload.put("reputationTrends", trends);
            }

            // 差评归因
            payload.put("issueStats", issueStats);

            // 商家亮点
            if (!highlights.isEmpty()) {
                List<Map<String, Object>> hlList = new ArrayList<>();
                for (MerchantHighlight h : highlights) {
                    Map<String, Object> hl = new LinkedHashMap<>();
                    hl.put("highlightType", h.getHighlightType());
                    hl.put("title", h.getTitle());
                    hl.put("description", h.getDescription());
                    hl.put("mentionCount", h.getMentionCount());
                    hl.put("positiveRatio", h.getPositiveRatio());
                    hlList.add(hl);
                }
                payload.put("highlights", hlList);
            }

            // 竞品数据
            if (!competitorData.isEmpty()) {
                payload.put("competitors", competitorData);
            }

            return payload;
        }
    }
}
