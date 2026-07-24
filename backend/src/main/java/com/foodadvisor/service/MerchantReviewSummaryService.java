package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.dto.summary.MerchantReviewSummaryVO;
import com.foodadvisor.dto.summary.SummaryEvidenceVO;
import com.foodadvisor.dto.summary.SummaryPointVO;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.MerchantReviewSummary;
import com.foodadvisor.entity.MerchantSummaryEvidence;
import com.foodadvisor.entity.Review;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.MerchantReviewSummaryMapper;
import com.foodadvisor.mapper.MerchantSummaryEvidenceMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.entity.AiRequestTraceStage;
import com.foodadvisor.trace.AiTraceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 商家评价智能总结服务（EPIC-01 Story 7）
 *
 * 摘要生成后落库缓存，用户端读取不再调用模型；
 * 新增评论达到阈值或摘要过期后可生成新版本。
 */
@Service
public class MerchantReviewSummaryService {
    private static final Logger log =
            LoggerFactory.getLogger(MerchantReviewSummaryService.class);
    private static final String REVIEW_SOURCE_TYPE = "REVIEW";
    private static final String SOURCE_UNAVAILABLE = "SOURCE_UNAVAILABLE";
    private static final Set<String> EVIDENCE_TYPES = Set.of(
            "ADVANTAGE", "DISADVANTAGE", "DISH",
            "ENVIRONMENT", "SERVICE", "RECENT_CHANGE"
    );

    /** 生成摘要所需的最少有效评论数（验收准则 1/5） */
    private static final int MIN_REVIEW_COUNT = 3;

    /** 单次送入模型的最大评论条数，取最新的 N 条，防止提示词过长 */
    private static final int MAX_REVIEWS_FOR_SUMMARY = 100;

    /** 新增评论达到该数量时才允许刷新摘要（验收准则 7） */
    private static final int REFRESH_NEW_REVIEW_THRESHOLD = 5;

    /** 摘要最长有效天数，超过后允许刷新 */
    private static final int REFRESH_MAX_AGE_DAYS = 7;

    private final MerchantReviewSummaryMapper summaryMapper;
    private final MerchantSummaryEvidenceMapper evidenceMapper;
    private final ReviewMapper reviewMapper;
    private final MerchantMapper merchantMapper;
    private final AIClientService aiClientService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiRequestTraceService traceService;

    /** 防止同一商家重复触发异步生成 */
    private final Set<Long> pendingGenerations = ConcurrentHashMap.newKeySet();

    public MerchantReviewSummaryService(
            MerchantReviewSummaryMapper summaryMapper,
            MerchantSummaryEvidenceMapper evidenceMapper,
            ReviewMapper reviewMapper,
            MerchantMapper merchantMapper,
            AIClientService aiClientService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this(summaryMapper, evidenceMapper, reviewMapper, merchantMapper, aiClientService,
                jdbcTemplate, objectMapper, null);
    }

    @Autowired
    public MerchantReviewSummaryService(
            MerchantReviewSummaryMapper summaryMapper,
            MerchantSummaryEvidenceMapper evidenceMapper,
            ReviewMapper reviewMapper,
            MerchantMapper merchantMapper,
            AIClientService aiClientService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AiRequestTraceService traceService
    ) {
        this.summaryMapper = summaryMapper;
        this.evidenceMapper = evidenceMapper;
        this.reviewMapper = reviewMapper;
        this.merchantMapper = merchantMapper;
        this.aiClientService = aiClientService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.traceService = traceService;
    }

    /**
     * 读取商家当前可展示的摘要 — 缓存命中直接返回；
     * 若从未生成且评论数达标，则异步触发首次生成，前端轮询等待；
     * 若已有摘要但缺少依据（旧版本数据），则异步强制刷新一次（验收准则 1/3）。
     */
    public MerchantReviewSummaryVO getDisplaySummary(Long merchantId) {
        MerchantReviewSummary latest = findLatestDisplayable(merchantId);

        // 冷却期内不触发任何自动生成，防止失败死循环
        boolean inCooldown = isInGenerationCooldown(merchantId);

        if (latest == null) {
            int reviewCount = countPublicReviews(merchantId);
            if (!inCooldown && reviewCount >= MIN_REVIEW_COUNT) {
                log.info("首次访问触发摘要异步生成: merchantId={}, reviewCount={}",
                        merchantId, reviewCount);
                triggerAsyncGeneration(merchantId, false);
                return generatingResponse(merchantId, reviewCount);
            }

            MerchantReviewSummaryVO vo = new MerchantReviewSummaryVO();
            vo.setMerchantId(merchantId);
            vo.setReviewCount(reviewCount);
            vo.setStatus("NONE");
            vo.setMinimumReviewCount(MIN_REVIEW_COUNT);
            return vo;
        }

        // INSUFFICIENT_DATA → 检查评论是否已达门槛
        if ("INSUFFICIENT_DATA".equals(latest.getStatus())) {
            int currentCount = countPublicReviews(merchantId);
            if (!inCooldown && currentCount >= MIN_REVIEW_COUNT) {
                log.info("评论数已达门槛, 触发异步生成: merchantId={}, reviewCount={}",
                        merchantId, currentCount);
                triggerAsyncGeneration(merchantId, false);
                return generatingResponse(merchantId, currentCount);
            }
            MerchantReviewSummaryVO vo = toVO(latest);
            vo.setReviewCount(currentCount);
            return vo;
        }

        // SUCCESS — 检查是否需要刷新（冷却期内跳过）
        if ("SUCCESS".equals(latest.getStatus())) {
            int currentCount = countPublicReviews(merchantId);
            int cachedCount = latest.getReviewCount() != null ? latest.getReviewCount() : 0;

            // 评论数有变化 → 自动刷新
            if (!inCooldown && currentCount != cachedCount && currentCount >= MIN_REVIEW_COUNT) {
                log.info("评论数变化触发自动刷新: merchantId={}, cached={}, current={}",
                        merchantId, cachedCount, currentCount);
                triggerAsyncGeneration(merchantId, true);
                return generatingResponse(merchantId, currentCount);
            }

            // 缺少依据记录 → 异步强制刷新
            if (!inCooldown) {
                Long evidenceCount = evidenceMapper.selectCount(
                        new LambdaQueryWrapper<MerchantSummaryEvidence>()
                                .eq(MerchantSummaryEvidence::getSummaryId, latest.getId())
                );
                if (evidenceCount == 0) {
                    if (currentCount >= MIN_REVIEW_COUNT) {
                        log.info("摘要缺少依据, 触发异步刷新: merchantId={}, summaryId={}",
                                merchantId, latest.getId());
                        triggerAsyncGeneration(merchantId, true);
                        return generatingResponse(merchantId, currentCount);
                    }
                }
            }
        }

        return toVO(latest);
    }

    /**
     * 用户端手动刷新摘要 — 异步强制重新生成，立即返回 GENERATING。
     */
    public MerchantReviewSummaryVO refreshSummary(Long merchantId) {
        int reviewCount = countPublicReviews(merchantId);
        if (reviewCount < MIN_REVIEW_COUNT) {
            MerchantReviewSummaryVO vo = new MerchantReviewSummaryVO();
            vo.setMerchantId(merchantId);
            vo.setReviewCount(reviewCount);
            vo.setStatus("INSUFFICIENT_DATA");
            vo.setMinimumReviewCount(MIN_REVIEW_COUNT);
            return vo;
        }

        log.info("用户触发摘要刷新: merchantId={}, reviewCount={}", merchantId, reviewCount);
        triggerAsyncGeneration(merchantId, true);
        return generatingResponse(merchantId, reviewCount);
    }

    /**
     * 生成或刷新摘要。
     *
     * 注意：这里刻意不加 @Transactional —— 失败路径要"先插 FAILED 记录再抛异常"，
     * 如果整个方法在一个事务里，抛出的异常会把 FAILED 记录一起回滚掉。
     *
     * @param force true 时跳过刷新条件检查，强制重新生成
     */
    public MerchantReviewSummaryVO generateSummary(Long merchantId, boolean force) {
        AiTraceContext context = traceService == null ? null
                : traceService.startTrace(null, null, null, "MERCHANT_REVIEW_SUMMARY");
        try {
            return generateSummary(merchantId, force, context);
        } catch (RuntimeException exception) {
            failTrace(context, "SUMMARY_GENERATION_FAILED", exception.getMessage());
            throw exception;
        }
    }

    public MerchantReviewSummaryVO generateSummary(
            Long merchantId, boolean force, AiTraceContext context
    ) {
        recordRequest(context, merchantId);
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "MERCHANT_NOT_FOUND",
                    "商家不存在"
            );
        }

        // 只用公开评价：已发布 + 审核通过 + 原始评价（与公开列表口径一致）
        List<Review> reviews = loadPublicReviews(merchantId);
        completeStage(startStage(context, "REVIEW_SOURCE_LOAD", Map.of(
                        "merchantId", merchantId, "reviewIds", reviews.stream().map(Review::getId).toList(),
                        "reviewCount", reviews.size())),
                Map.of("reviewCount", reviews.size()), null, null, null, null);
        MerchantReviewSummary latest = findLatestDisplayable(merchantId);

        // ==== 评论不足：不调模型，落一条 INSUFFICIENT_DATA 记录（验收准则 5） ====
        if (reviews.size() < MIN_REVIEW_COUNT) {
            // 上一条已经是同样评论数的"数据不足"记录时直接复用，避免重复插行
            if (latest != null
                    && "INSUFFICIENT_DATA".equals(latest.getStatus())
                    && latest.getReviewCount() == reviews.size()) {
                MerchantReviewSummaryVO response = toVO(latest);
                finishFallback(context, response, merchantId, reviews.size(), 0, "REUSED_INSUFFICIENT_DATA");
                return response;
            }

            Long summaryId = insertSummaryRow(
                    merchantId,
                    nextVersion(merchantId),
                    null, "[]", "[]", "[]", "{}", "{}", "[]",
                    reviews.size(), null, null,
                    "INSUFFICIENT_DATA", null, null, null
            );
            MerchantReviewSummaryVO response = toVO(summaryMapper.selectById(summaryId));
            finishFallback(context, response, merchantId, reviews.size(), 0, "INSUFFICIENT_DATA");
            return response;
        }

        // ==== 缓存命中：未达刷新条件时返回已有摘要（验收准则 7） ====
        if (!force
                && latest != null
                && "SUCCESS".equals(latest.getStatus())
                && !needsRefresh(latest, reviews.size())) {
            MerchantReviewSummaryVO response = toVO(latest);
            finishFallback(context, response, merchantId, reviews.size(), 0, "REUSED_SUMMARY");
            return response;
        }

        // ==== 调用 AI 服务生成 ====
        int version = nextVersion(merchantId);

        List<Map<String, Object>> reviewPayload = new ArrayList<>();
        for (Review review : reviews) {
            Map<String, Object> item = new HashMap<>();
            item.put("reviewId", review.getId());
            item.put("rating", review.getRating());
            item.put("content", review.getContent());
            item.put("reviewTime",
                    review.getPublishedAt() != null
                            ? review.getPublishedAt().toString()
                            : null);
            reviewPayload.add(item);
        }

        completeStage(startStage(context, "PROMPT_BUILD", Map.of("merchantId", merchantId,
                        "reviewCount", reviews.size(), "promptVersion", "review-summary:v1")),
                Map.of("reviewCount", reviews.size()), null, null, null, "review-summary:v1");

        JsonNode result;
        try {
            result = traceService == null || context == null
                    ? aiClientService.generateReviewSummary(merchantId, version, reviewPayload, MIN_REVIEW_COUNT)
                    : aiClientService.generateReviewSummary(merchantId, version, reviewPayload,
                            MIN_REVIEW_COUNT, context);
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("AI摘要生成调用失败: merchantId={}, error={}", merchantId, detail);
            // 记录失败版本方便排查，同时向前端返回明确错误
            insertSummaryRow(
                    merchantId, version,
                    null, "[]", "[]", "[]", "{}", "{}", "[]",
                    reviews.size(), null, null,
                    "FAILED", null, null,
                    truncate(detail, 500)
            );
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "SUMMARY_GENERATION_FAILED",
                    "摘要生成失败，原因：" + truncate(detail, 200)
            );
        }

        String summaryStatus = result.has("summaryStatus")
                ? result.get("summaryStatus").asText()
                : "FAILED";

        completeStage(startStage(context, "OUTPUT_VALIDATION", Map.of("merchantId", merchantId)),
                Map.of("status", summaryStatus), "FASTAPI", textOrNull(result, "modelName"),
                textOrNull(result, "modelVersion"), "review-summary:v1");

        if ("FAILED".equals(summaryStatus)) {
            String aiError = textOrNull(result, "errorMessage");
            String detail = aiError != null ? "，原因：" + aiError : "";
            insertSummaryRow(
                    merchantId, version,
                    null, "[]", "[]", "[]", "{}", "{}", "[]",
                    reviews.size(), null, null,
                    "FAILED",
                    textOrNull(result, "modelName"), null,
                    aiError
            );
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "SUMMARY_GENERATION_FAILED",
                    "摘要生成失败" + detail
            );
        }

        // ==== 落库：摘要主记录 ====
        OffsetDateTime sourceStart = reviews.stream()
                .map(Review::getPublishedAt)
                .filter(t -> t != null)
                .min(OffsetDateTime::compareTo)
                .orElse(null);
        OffsetDateTime sourceEnd = reviews.stream()
                .map(Review::getPublishedAt)
                .filter(t -> t != null)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        Long summaryId = insertSummaryRow(
                merchantId, version,
                textOrNull(result, "summaryText"),
                jsonOrDefault(result, "advantages", "[]"),
                jsonOrDefault(result, "disadvantages", "[]"),
                jsonOrDefault(result, "recommendedDishes", "[]"),
                jsonOrDefault(result, "environmentSummary", "{}"),
                jsonOrDefault(result, "serviceSummary", "{}"),
                jsonOrDefault(result, "recentChanges", "[]"),
                reviews.size(), sourceStart, sourceEnd,
                "SUCCESS",
                textOrNull(result, "modelName"),
                null,
                null
        );

        AiRequestTraceStage evidenceStage = startStage(context, "EVIDENCE_SELECTION",
                Map.of("summaryId", summaryId, "merchantId", merchantId));

        // ==== 落库：摘要依据（二次校验 reviewId 真实性，验收准则 3/6） ====
        Set<Long> sentReviewIds = reviews.stream()
                .map(Review::getId)
                .collect(Collectors.toSet());
        Map<Long, Review> sentReviews = reviews.stream()
                .collect(Collectors.toMap(Review::getId, review -> review));

        if (result.has("evidences") && result.get("evidences").isArray()) {
            for (JsonNode ev : result.get("evidences")) {
                long reviewId = ev.has("reviewId") ? ev.get("reviewId").asLong() : -1;
                String evidenceType = textOrNull(ev, "evidenceType");
                evidenceType = evidenceType == null
                        ? null : evidenceType.toUpperCase();
                Review sourceReview = sentReviews.get(reviewId);

                if (!sentReviewIds.contains(reviewId)
                        || sourceReview == null
                        || !merchantId.equals(sourceReview.getMerchantId())
                        || !isPublicReview(sourceReview)
                        || !EVIDENCE_TYPES.contains(evidenceType)) {
                    log.warn(
                            "Discarding invalid summary evidence: summaryId={}, "
                                    + "reviewId={}, expectedMerchantId={}, "
                                    + "actualMerchantId={}",
                            summaryId,
                            reviewId,
                            merchantId,
                            sourceReview == null
                                    ? null : sourceReview.getMerchantId()
                    );
                    continue;
                }

                jdbcTemplate.update(
                        """
                        INSERT INTO merchant_summary_evidences (
                            summary_id, review_id,
                            source_merchant_id, source_type,
                            evidence_type, evidence_excerpt, created_at
                        )
                        VALUES (?, ?, ?, 'REVIEW', ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (summary_id, review_id, evidence_type)
                        DO NOTHING
                        """,
                        summaryId,
                        reviewId,
                        merchantId,
                        evidenceType,
                        textOrNull(ev, "evidenceExcerpt")
                );
            }
        }

        MerchantReviewSummaryVO response = toVO(summaryMapper.selectById(summaryId));
        int evidenceCount = result.has("evidences") && result.get("evidences").isArray()
                ? result.get("evidences").size() : 0;
        completeStage(evidenceStage, Map.of("summaryId", summaryId, "evidenceCount", evidenceCount),
                null, null, null, null);
        completeStage(startStage(context, "RESULT_PERSIST", Map.of("summaryId", summaryId)),
                Map.of("summaryId", summaryId, "status", summaryStatus), null,
                textOrNull(result, "modelName"), textOrNull(result, "modelVersion"), "review-summary:v1");
        finishSuccess(context, response, merchantId, reviews.size(), evidenceCount,
                textOrNull(result, "modelName"), textOrNull(result, "modelVersion"));
        return response;
    }

    /**
     * 查询摘要依据列表（"查看依据"溯源入口）。
     *
     * @param summaryId    可选，默认取该商家最新可展示摘要
     * @param evidenceType 可选，按依据类型筛选
     */
    public List<SummaryEvidenceVO> getEvidences(
            Long merchantId,
            Long summaryId,
            String evidenceType
    ) {
        MerchantReviewSummary summary;
        if (summaryId != null) {
            summary = summaryMapper.selectById(summaryId);
            // 防跨商家读取
            if (summary == null || !summary.getMerchantId().equals(merchantId)) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SUMMARY_NOT_FOUND",
                        "摘要不存在"
                );
            }
        } else {
            summary = findLatestDisplayable(merchantId);
            if (summary == null) {
                return List.of();
            }
        }

        LambdaQueryWrapper<MerchantSummaryEvidence> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.eq(MerchantSummaryEvidence::getSummaryId, summary.getId());
        if (evidenceType != null && !evidenceType.isBlank()) {
            wrapper.eq(
                    MerchantSummaryEvidence::getEvidenceType,
                    evidenceType.toUpperCase()
            );
        }

        List<MerchantSummaryEvidence> evidences =
                evidenceMapper.selectList(wrapper);
        if (evidences.isEmpty()) {
            return List.of();
        }

        // 批量查原评价，组装展示信息
        List<Long> reviewIds = evidences.stream()
                .map(MerchantSummaryEvidence::getReviewId)
                .distinct()
                .toList();
        Map<Long, Review> reviewMap = reviewMapper.selectBatchIds(reviewIds)
                .stream()
                .collect(Collectors.toMap(Review::getId, r -> r));
        Merchant merchant = merchantMapper.selectById(summary.getMerchantId());
        String merchantName = merchant == null ? null : merchant.getName();

        List<SummaryEvidenceVO> vos = new ArrayList<>();
        for (MerchantSummaryEvidence evidence : evidences) {
            SummaryEvidenceVO vo = new SummaryEvidenceVO();
            vo.setEvidenceId(evidence.getId());
            vo.setSourceType(REVIEW_SOURCE_TYPE);
            vo.setMerchantId(summary.getMerchantId());
            vo.setMerchantName(merchantName);
            vo.setEvidenceType(evidence.getEvidenceType());

            Review review = reviewMap.get(evidence.getReviewId());
            Long expectedMerchantId = summary.getMerchantId();

            // 可用性校验：原评价存在、属于当前商家、来源商家一致、且处于公开状态
            boolean available =
                    expectedMerchantId != null
                            && review != null
                            && expectedMerchantId.equals(review.getMerchantId())
                            && expectedMerchantId.equals(evidence.getSourceMerchantId())
                            && isPublicReview(review);
            vo.setReviewAvailable(available);
            vo.setAvailable(available);
            if (available) {
                vo.setSourceId(evidence.getReviewId());
                vo.setReviewId(evidence.getReviewId());
                vo.setRating(review.getRating());
                vo.setReviewContent(review.getContent());
                vo.setEvidenceExcerpt(evidence.getEvidenceExcerpt());
                vo.setReviewTime(review.getPublishedAt() != null
                        ? review.getPublishedAt() : review.getReviewTime());
            } else {
                vo.setUnavailableReason(SOURCE_UNAVAILABLE);
                log.warn(
                        "Unavailable summary evidence: evidenceId={}, "
                                + "summaryId={}, reviewId={}, "
                                + "expectedMerchantId={}, "
                                + "storedSourceMerchantId={}, "
                                + "actualReviewMerchantId={}",
                        evidence.getId(),
                        summary.getId(),
                        evidence.getReviewId(),
                        expectedMerchantId,
                        evidence.getSourceMerchantId(),
                        review == null
                                ? null
                                : review.getMerchantId()
                );
            }
            vos.add(vo);
        }
        return vos;
    }

    // ==================== 私有方法 ====================

    /** 查最新一条可展示摘要（排除 FAILED，保证失败不覆盖旧的可用摘要） */
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

    private void finishSuccess(AiTraceContext context, MerchantReviewSummaryVO response,
                               Long merchantId, int reviewCount, int evidenceCount,
                               String modelName, String modelVersion) {
        if (context == null || traceService == null) return;
        traceService.completeTrace(context, "SUCCESS",
                summaryOutput(response, merchantId, reviewCount, evidenceCount, false),
                "FASTAPI", modelName, modelVersion, "review-summary:v1");
    }

    private void finishFallback(AiTraceContext context, MerchantReviewSummaryVO response,
                                Long merchantId, int reviewCount, int evidenceCount, String reason) {
        if (context == null || traceService == null) return;
        Map<String, Object> output = summaryOutput(response, merchantId, reviewCount, evidenceCount, true);
        output.put("fallbackReason", reason);
        traceService.completeTrace(context, "FALLBACK", output, "RULE_ENGINE", "RULE_ENGINE",
                null, "NOT_APPLICABLE");
    }

    private Map<String, Object> summaryOutput(MerchantReviewSummaryVO response, Long merchantId,
                                              int reviewCount, int evidenceCount, boolean degraded) {
        Map<String, Object> output = new HashMap<>();
        output.put("summaryId", response == null ? null : response.getSummaryId());
        output.put("merchantId", merchantId);
        output.put("reviewCount", reviewCount);
        output.put("evidenceCount", evidenceCount);
        output.put("status", response == null ? "FAILED" : response.getStatus());
        output.put("degraded", degraded);
        return output;
    }

    private void failTrace(AiTraceContext context, String code, String message) {
        if (context != null && traceService != null) traceService.failTraceSafely(context, code, message);
    }

    private MerchantReviewSummary findLatestDisplayable(Long merchantId) {
        return summaryMapper.selectOne(
                new LambdaQueryWrapper<MerchantReviewSummary>()
                        .eq(MerchantReviewSummary::getMerchantId, merchantId)
                        .ne(MerchantReviewSummary::getStatus, "FAILED")
                        .orderByDesc(MerchantReviewSummary::getVersion)
                        .last("LIMIT 1")
        );
    }

    /**
     * 查最新一条记录（含 FAILED），判断是否在冷却期内。
     * 若最近 120 秒内已尝试过生成，不再重复触发，防止失败死循环。
     */
    private boolean isInGenerationCooldown(Long merchantId) {
        MerchantReviewSummary latest = summaryMapper.selectOne(
                new LambdaQueryWrapper<MerchantReviewSummary>()
                        .eq(MerchantReviewSummary::getMerchantId, merchantId)
                        .orderByDesc(MerchantReviewSummary::getVersion)
                        .last("LIMIT 1")
        );
        if (latest == null || latest.getGeneratedAt() == null) return false;
        long secondsSinceLast = Duration.between(
                latest.getGeneratedAt(), OffsetDateTime.now()).getSeconds();
        return secondsSinceLast < 120;
    }

    /** 下一个版本号 = 当前最大版本 + 1（含 FAILED 记录，保证唯一约束不冲突） */
    private int nextVersion(Long merchantId) {
        MerchantReviewSummary latest = summaryMapper.selectOne(
                new LambdaQueryWrapper<MerchantReviewSummary>()
                        .eq(MerchantReviewSummary::getMerchantId, merchantId)
                        .orderByDesc(MerchantReviewSummary::getVersion)
                        .last("LIMIT 1")
        );
        return latest == null ? 1 : latest.getVersion() + 1;
    }

    /** 刷新条件：新增评论达到阈值，或摘要生成时间超过有效期 */
    private boolean needsRefresh(
            MerchantReviewSummary latest,
            int currentReviewCount
    ) {
        int newReviews = currentReviewCount
                - (latest.getReviewCount() == null ? 0 : latest.getReviewCount());
        if (newReviews >= REFRESH_NEW_REVIEW_THRESHOLD) {
            return true;
        }

        if (latest.getGeneratedAt() != null) {
            Duration age = Duration.between(
                    latest.getGeneratedAt(), OffsetDateTime.now());
            return age.toDays() >= REFRESH_MAX_AGE_DAYS;
        }
        return false;
    }

    /** 加载公开评价：已发布 + 审核通过（含原始评价和追评），取最新 N 条 */
    private List<Review> loadPublicReviews(Long merchantId) {
        return reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getMerchantId, merchantId)
                        .in(Review::getReviewType, "ORIGINAL", "FOLLOW_UP")
                        .eq(Review::getStatus, "PUBLISHED")
                        .eq(Review::getModerationStatus, "APPROVED")
                        .orderByDesc(Review::getPublishedAt)
                        .last("LIMIT " + MAX_REVIEWS_FOR_SUMMARY)
        );
    }

    /** 统计公开有效评论数（含原始评价和追评） */
    private int countPublicReviews(Long merchantId) {
        return Math.toIntExact(reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getMerchantId, merchantId)
                        .in(Review::getReviewType, "ORIGINAL", "FOLLOW_UP")
                        .eq(Review::getStatus, "PUBLISHED")
                        .eq(Review::getModerationStatus, "APPROVED")
        ));
    }

    /** 异步触发摘要生成（防重），调用方立即返回 GENERATING 状态 */
    private boolean triggerAsyncGeneration(Long merchantId, boolean force) {
        if (!pendingGenerations.add(merchantId)) {
            log.info("跳过重复异步生成: merchantId={}", merchantId);
            return false;
        }
        new Thread(() -> {
            try {
                generateSummary(merchantId, force);
            } catch (Exception e) {
                log.error("异步生成摘要失败: merchantId={}, error={}",
                        merchantId, e.getMessage());
            } finally {
                pendingGenerations.remove(merchantId);
            }
        }, "summary-gen-" + merchantId).start();
        return true;
    }

    /** 构建 GENERATING 占位响应，供前端轮询 */
    private MerchantReviewSummaryVO generatingResponse(Long merchantId, int reviewCount) {
        MerchantReviewSummaryVO vo = new MerchantReviewSummaryVO();
        vo.setMerchantId(merchantId);
        vo.setStatus("GENERATING");
        vo.setReviewCount(reviewCount);
        vo.setMinimumReviewCount(MIN_REVIEW_COUNT);
        return vo;
    }

    private boolean isPublicReview(Review review) {
        return review != null
                && "PUBLISHED".equals(review.getStatus())
                && "APPROVED".equals(review.getModerationStatus())
                && review.getDeletedAt() == null;
    }

    /**
     * 插入摘要主记录。
     * JSONB 字段用原生 SQL + ?::jsonb 处理（同 ReviewService.saveAnalysis 的做法），
     * RETURNING id 拿回自增主键供依据表关联。
     */
    private Long insertSummaryRow(
            Long merchantId, int version, String summaryText,
            String advantages, String disadvantages, String recommendedDishes,
            String environmentSummary, String serviceSummary, String recentChanges,
            int reviewCount, OffsetDateTime sourceStart, OffsetDateTime sourceEnd,
            String status, String modelName, String modelVersion, String errorMessage
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO merchant_review_summaries (
                    merchant_id, version, summary_text,
                    advantages, disadvantages, recommended_dishes,
                    environment_summary, service_summary, recent_changes,
                    review_count, source_start_time, source_end_time,
                    status, model_name, model_version, error_message,
                    generated_at
                )
                VALUES (
                    ?, ?, ?,
                    ?::jsonb, ?::jsonb, ?::jsonb,
                    ?::jsonb, ?::jsonb, ?::jsonb,
                    ?, ?, ?,
                    ?, ?, ?, ?,
                    CURRENT_TIMESTAMP
                )
                RETURNING id
                """,
                Long.class,
                merchantId, version, summaryText,
                advantages, disadvantages, recommendedDishes,
                environmentSummary, serviceSummary, recentChanges,
                reviewCount, sourceStart, sourceEnd,
                status, modelName, modelVersion, errorMessage
        );
    }

    /** 实体 → 展示 VO，JSONB 字符串解析回结构化对象 */
    private MerchantReviewSummaryVO toVO(MerchantReviewSummary summary) {
        MerchantReviewSummaryVO vo = new MerchantReviewSummaryVO();
        vo.setSummaryId(summary.getId());
        vo.setMerchantId(summary.getMerchantId());
        vo.setVersion(summary.getVersion());
        vo.setStatus(summary.getStatus());
        vo.setSummaryText(summary.getSummaryText());
        vo.setReviewCount(summary.getReviewCount());
        vo.setMinimumReviewCount(MIN_REVIEW_COUNT);
        vo.setGeneratedAt(summary.getGeneratedAt());

        try {
            vo.setAdvantages(parsePoints(summary.getAdvantages()));
            vo.setDisadvantages(parsePoints(summary.getDisadvantages()));
            vo.setRecommendedDishes(parsePoints(summary.getRecommendedDishes()));
            vo.setEnvironmentSummary(
                    objectMapper.readTree(orDefault(summary.getEnvironmentSummary(), "{}")));
            vo.setServiceSummary(
                    objectMapper.readTree(orDefault(summary.getServiceSummary(), "{}")));
            vo.setRecentChanges(
                    objectMapper.readTree(orDefault(summary.getRecentChanges(), "[]")));
        } catch (Exception e) {
            // JSON 解析异常时给空结构，不让接口整体失败
            vo.setAdvantages(List.of());
            vo.setDisadvantages(List.of());
            vo.setRecommendedDishes(List.of());
        }
        return vo;
    }

    private List<SummaryPointVO> parsePoints(String json) throws Exception {
        return objectMapper.readValue(
                orDefault(json, "[]"),
                objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, SummaryPointVO.class)
        );
    }

    private String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText()
                : null;
    }

    private String jsonOrDefault(JsonNode node, String field, String fallback) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).toString()
                : fallback;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
