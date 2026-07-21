package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.ReviewAnalysisResultVO;
import com.foodadvisor.dto.ReviewBatchAnalysisResultVO;
import com.foodadvisor.dto.review.MyReviewDetailVO;
import com.foodadvisor.dto.review.MyReviewListVO;
import com.foodadvisor.dto.review.ReviewDisplayVO;
import com.foodadvisor.dto.review.ReviewFollowUpRequest;
import com.foodadvisor.dto.review.ReviewFollowUpVO;
import com.foodadvisor.dto.review.EditReplyDraftRequest;
import com.foodadvisor.dto.review.ReviewReplyDraftVO;
import com.foodadvisor.dto.review.ReviewReplyVO;
import com.foodadvisor.dto.review.ReviewSubmitRequest;
import com.foodadvisor.dto.review.ReviewSubmitResponse;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewAnalysis;
import com.foodadvisor.service.AIClientService;
import com.foodadvisor.service.ReviewReplyDraftService;
import com.foodadvisor.service.ReviewService;
import com.foodadvisor.service.AiRequestTraceService;
import com.foodadvisor.entity.AiRequestTraceStage;
import com.foodadvisor.trace.AiTraceContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.dto.review.ReviewTagStatVO;
import com.foodadvisor.entity.ReviewTag;
import com.foodadvisor.entity.ReviewTagRelation;
import com.foodadvisor.mapper.ReviewTagMapper;
import com.foodadvisor.mapper.TagRelationWithName;
import com.foodadvisor.entity.ReviewIssueCategory;
import com.foodadvisor.entity.ReviewIssueRelation;
import com.foodadvisor.mapper.ReviewIssueCategoryMapper;
import com.foodadvisor.dto.IssueStatVO;
import com.foodadvisor.dto.IssueReviewVO;
import com.foodadvisor.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.LocalDate;

/**
 * 评价接口 — 评论查询 & AI 分析
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final AIClientService aiClientService;
    private final ReviewTagMapper reviewTagMapper;
    private final ReviewIssueCategoryMapper issueCategoryMapper;
    private final ReviewReplyDraftService replyDraftService;
    private final AiRequestTraceService traceService;

    public ReviewController(
            ReviewService reviewService,
            AIClientService aiClientService,
            ReviewTagMapper reviewTagMapper,
            ReviewIssueCategoryMapper issueCategoryMapper,
            ReviewReplyDraftService replyDraftService
    ) {
        this(reviewService, aiClientService, reviewTagMapper, issueCategoryMapper,
                replyDraftService, null);
    }

    @Autowired
    public ReviewController(
            ReviewService reviewService,
            AIClientService aiClientService,
            ReviewTagMapper reviewTagMapper,
            ReviewIssueCategoryMapper issueCategoryMapper,
            ReviewReplyDraftService replyDraftService,
            AiRequestTraceService traceService
    ) {
        this.reviewService = reviewService;
        this.aiClientService = aiClientService;
        this.reviewTagMapper = reviewTagMapper;
        this.issueCategoryMapper = issueCategoryMapper;
        this.replyDraftService = replyDraftService;
        this.traceService = traceService;
    }

    @PostMapping("/merchants/{merchantId}")
    public ApiResponse<ReviewSubmitResponse> submit(
            @PathVariable Long merchantId,
            @ModelAttribute ReviewSubmitRequest request,
            @RequestPart(value = "images", required = false)
            List<MultipartFile> images,
            HttpServletRequest servletRequest
    ) {
        ReviewSubmitResponse response =
                reviewService.submitOriginalReview(
                        requireUserId(servletRequest),
                        merchantId,
                        request,
                        images == null ? List.of() : images
                );
        return ApiResponse.success(response);
    }

    private Long requireUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }

        throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication required"
        );
    }

    /**
     * 按商家分页查询评价
     * 支持可选的标签和情感倾向筛选（EPIC-01 Story 8）。
     * @param merchantId 商家 ID
     * @param pageNum    页码，默认 1
     * @param pageSize   每页条数，默认 10
     * @param tagCode    可选，按标签编码筛选（如 TASTE_GOOD）
     * @param sentiment  可选，按情感倾向筛选（POSITIVE / NEGATIVE / NEUTRAL）
     *                   仅在 tagCode 不为空时生效
     */
    @GetMapping
    public ApiResponse<PageResult<ReviewDisplayVO>> list(
            @RequestParam Long merchantId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String tagCode,
            @RequestParam(required = false) String sentiment
            ) {
        Page<ReviewDisplayVO> page = reviewService.listByMerchantWithUser(
                merchantId, pageNum, pageSize, tagCode, sentiment);
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 触发单条评价的 AI 分析（调用 FastAPI）（V0.3 更新）
     */
    @PostMapping("/{reviewId}/analyze")
    public ApiResponse<ReviewAnalysisResultVO> analyze(
            @PathVariable Long reviewId,
            HttpServletResponse servletResponse
    ) {
        Review review = reviewService.getById(reviewId);
        if (review == null) {
            return ApiResponse.notFound("评价不存在");
        }

        AiTraceContext context = startTrace("REVIEW_ANALYSIS", review.getId());
        try {
            recordRequestReceived(context, "SINGLE_REVIEW_ANALYSIS", 1);
            ReviewAnalysisResultVO response = analyzeReview(review, context, 1);
            finishSingleTrace(context, response);
            setTraceHeader(servletResponse, context);
            return ApiResponse.success(response);
        } catch (RuntimeException exception) {
            failTrace(context, "REVIEW_ANALYSIS_FAILED", exception.getMessage(),
                    Map.of("reviewId", review.getId(), "status", "FAILED", "degraded", true));
            setTraceHeader(servletResponse, context);
            throw exception;
        }
    }

    private ReviewAnalysisResultVO analyzeReview(
            Review review, AiTraceContext context, int attemptNo
    ) {
        AiRequestTraceStage reviewStage = startStage(context, "REVIEW_ANALYSIS", attemptNo,
                Map.of("reviewId", review.getId(), "merchantId", review.getMerchantId()));
        int reviewVersion = review.getCurrentVersion() != null ? review.getCurrentVersion() : 1;

        // 调用 FastAPI 分析
        AiRequestTraceStage promptStage = startStage(context, "PROMPT_BUILD", attemptNo,
                Map.of("reviewId", review.getId(), "contentLength",
                        review.getContent() == null ? 0 : review.getContent().length()));
        completeStage(promptStage, Map.of("reviewId", review.getId(), "promptVersion", "review-analysis:v1"),
                null, null, null, "review-analysis:v1");

        JsonNode result = (traceService == null || context == null)
                ? aiClientService.analyzeReview(review.getId(), review.getMerchantId(),
                        review.getContent(), reviewVersion)
                : aiClientService.analyzeReview(review.getId(), review.getMerchantId(),
                        review.getContent(), reviewVersion, context);

        AiRequestTraceStage validationStage = startStage(context, "OUTPUT_VALIDATION", attemptNo,
                Map.of("reviewId", review.getId()));
        validateAnalysisResult(result);
        completeStage(validationStage, Map.of("reviewId", review.getId(),
                        "status", result.path("status").asText("SUCCESS")),
                "FASTAPI", result.path("modelName").asText(null),
                result.path("modelVersion").asText(null), promptVersion(result));

        AiRequestTraceStage persistStage = startStage(context, "RESULT_PERSIST", attemptNo,
                Map.of("reviewId", review.getId()));

        // 保存分析结果到数据库
        ReviewAnalysis analysis = new ReviewAnalysis();
        analysis.setReviewId(review.getId());
        analysis.setReviewVersion(reviewVersion);
        analysis.setAnalysisVersion(result.has("analysisVersion") ? result.get("analysisVersion").asInt() : 1);
        analysis.setSentiment(result.get("sentiment").asText());
        analysis.setConfidence(new BigDecimal(result.get("confidence").asText()));
        analysis.setLowConfidence(result.has("lowConfidence") ? result.get("lowConfidence").asBoolean() : false);
        analysis.setKeywords(result.get("keywords").toString());
        analysis.setAspects(result.get("aspects").toString());
        if (result.has("negativeReason") && !result.get("negativeReason").isNull()) {
            analysis.setNegativeReason(result.get("negativeReason").asText());
        }
        analysis.setModelName(result.has("modelName") ? result.get("modelName").asText() : null);
        analysis.setModelVersion(result.has("modelVersion") && !result.get("modelVersion").isNull()
                ? result.get("modelVersion").asText() : null);
        // Spring 根 trace 优先于 ai-service 的局部业务编号，保证跨服务关联稳定。
        analysis.setBusinessTraceId(context == null ? (result.has("businessTraceId")
                ? result.get("businessTraceId").asText() : null) : context.traceId());
        analysis.setStatus(result.has("status") ? result.get("status").asText() : "SUCCESS");
        if (result.has("errorMessage") && !result.get("errorMessage").isNull()) {
            analysis.setErrorMessage(result.get("errorMessage").asText());
        }
        reviewService.saveAnalysis(analysis);

// ====== 持久化评价标签关联======
// AI 分析结果中的 tags 字段包含了从评论中提取的标签列表。
// 需要把这些标签关联写入 review_tag_relations 表，
// 这样后续的标签统计和筛选功能才有数据可用。
if (result.has("tags") && !result.get("tags").isNull() && result.get("tags").isArray()) {
    List<ReviewTagRelation> relations = new ArrayList<>();

    for (JsonNode tagNode : result.get("tags")) {
        String tagCode = tagNode.has("tagCode")
                ? tagNode.get("tagCode").asText()
                : null;

        if (tagCode == null || tagCode.isBlank()) {
            continue;  // 跳过没有 tagCode 的异常数据
        }

        // 通过 tagCode 从 review_tags 字典表找到对应的标签 ID
        // 只有字典中存在的标签才会被保存
        ReviewTag tag = reviewTagMapper.selectOne(
                new LambdaQueryWrapper<ReviewTag>()
                        .eq(ReviewTag::getCode, tagCode)
                        .eq(ReviewTag::getStatus, "ACTIVE")
        );

        if (tag == null) {
            continue;  // 标签编码不在预定义字典中，丢弃
        }

        // 构建关联记录
        ReviewTagRelation relation = new ReviewTagRelation();
        relation.setReviewId(review.getId());
        relation.setReviewVersion(reviewVersion);
        relation.setTagId(tag.getId());
        relation.setSentiment(
                tagNode.has("sentiment")
                        ? tagNode.get("sentiment").asText().toUpperCase()
                        : "NEUTRAL"
        );
        relation.setConfidence(
                tagNode.has("confidence")
                        ? new BigDecimal(tagNode.get("confidence").asText())
                        : BigDecimal.valueOf(0.5)
        );
        relation.setEvidenceText(
                tagNode.has("evidenceText") && !tagNode.get("evidenceText").isNull()
                        ? tagNode.get("evidenceText").asText()
                        : null
        );
        relation.setModelName(
                tagNode.has("modelName") && !tagNode.get("modelName").isNull()
                        ? tagNode.get("modelName").asText()
                        : null
        );

        relations.add(relation);
    }

    // 批量保存：先删旧关联再插入新的
    if (!relations.isEmpty()) {
        reviewService.saveTagRelations(relations);
    }
}

        // ====== 持久化差评归因关联（V0.3）======
        // AI 分析结果中的 issueCategories 包含每条差评的问题归类。
        // 需要写入 review_issue_relations 表，供商家端差评归因统计使用。
        if (result.has("issueCategories") && !result.get("issueCategories").isNull()
                && result.get("issueCategories").isArray()) {
            List<ReviewIssueRelation> issueRelations = new ArrayList<>();

            for (JsonNode issueNode : result.get("issueCategories")) {
                String catCode = issueNode.has("category")
                        ? issueNode.get("category").asText()
                        : null;

                if (catCode == null || catCode.isBlank()) {
                    continue;
                }

                // 通过类别编码查字典表取得 ID
                ReviewIssueCategory cat = issueCategoryMapper.selectOne(
                        new LambdaQueryWrapper<ReviewIssueCategory>()
                                .eq(ReviewIssueCategory::getCode, catCode)
                                .eq(ReviewIssueCategory::getStatus, "ACTIVE")
                );

                if (cat == null) {
                    continue; // 不在预定义类别中，跳过
                }

                ReviewIssueRelation relation = new ReviewIssueRelation();
                relation.setReviewId(review.getId());
                relation.setReviewVersion(reviewVersion);
                relation.setIssueCategoryId(cat.getId());
                relation.setConfidence(
                        issueNode.has("confidence")
                                ? new BigDecimal(issueNode.get("confidence").asText())
                                : BigDecimal.valueOf(0.5)
                );
                relation.setEvidenceText(
                        issueNode.has("evidenceText")
                                && !issueNode.get("evidenceText").isNull()
                                ? issueNode.get("evidenceText").asText()
                                : null
                );

                issueRelations.add(relation);
            }

            if (!issueRelations.isEmpty()) {
                reviewService.saveIssueRelations(issueRelations);
            }
        }


        // 构建返回 VO
        ReviewAnalysisResultVO vo = buildResultVO(review, result);
        ReviewAnalysis persisted = reviewService.getAnalysis(review.getId());
        vo.setReviewAnalysisId(persisted == null ? null : persisted.getId());
        vo.setTraceId(context == null ? vo.getBusinessTraceId() : context.traceId());
        vo.setBusinessTraceId(context == null ? vo.getBusinessTraceId() : context.traceId());
        boolean degraded = !"SUCCESS".equalsIgnoreCase(result.path("status").asText("SUCCESS"));
        completeStage(persistStage, analysisSummary(vo),
                null, vo.getModelName(), vo.getModelVersion(), promptVersion(result));
        if (degraded) {
            failStage(reviewStage, "REVIEW_ANALYSIS_DEGRADED",
                    result.path("errorMessage").asText("Review analysis returned a failed result"));
        } else {
            completeStage(reviewStage, analysisSummary(vo),
                    "FASTAPI", vo.getModelName(), vo.getModelVersion(), promptVersion(result));
        }
        return vo;
    }

    /**
     * 构建分析结果 VO（V0.3）
     */
    private ReviewAnalysisResultVO buildResultVO(Review review, JsonNode result) {
        ReviewAnalysisResultVO vo = new ReviewAnalysisResultVO();
        vo.setReviewId(review.getId());
        vo.setMerchantId(review.getMerchantId());
        vo.setReviewVersion(result.has("reviewVersion") ? result.get("reviewVersion").asInt() : 1);
        vo.setAnalysisVersion(result.has("analysisVersion") ? result.get("analysisVersion").asInt() : 1);
        vo.setSentiment(result.get("sentiment").asText());
        vo.setConfidence(new BigDecimal(result.get("confidence").asText()));
        vo.setLowConfidence(result.has("lowConfidence") ? result.get("lowConfidence").asBoolean() : false);
        vo.setBusinessTraceId(result.has("businessTraceId") && !result.get("businessTraceId").isNull()
                ? result.get("businessTraceId").asText() : null);

        // 解析 JSON 字段
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            vo.setKeywords(mapper.readValue(result.get("keywords").toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
            vo.setAspects(mapper.readValue(result.get("aspects").toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<ReviewAnalysisResultVO.AspectVO>>() {}));
            if (result.has("tags") && !result.get("tags").isNull()) {
                vo.setTags(mapper.readValue(result.get("tags").toString(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<ReviewAnalysisResultVO.TagResultVO>>() {}));
            }
            if (result.has("issueCategories") && !result.get("issueCategories").isNull()) {
                vo.setIssueCategories(mapper.readValue(result.get("issueCategories").toString(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<ReviewAnalysisResultVO.IssueCategoryVO>>() {}));
            }
        } catch (Exception ignored) {
            vo.setKeywords(new ArrayList<>());
            vo.setAspects(new ArrayList<>());
            vo.setTags(new ArrayList<>());
            vo.setIssueCategories(new ArrayList<>());
        }

        if (result.has("negativeReason") && !result.get("negativeReason").isNull()) {
            vo.setNegativeReason(result.get("negativeReason").asText());
        }
        vo.setModelName(result.has("modelName") ? result.get("modelName").asText() : null);
        vo.setModelVersion(result.has("modelVersion") && !result.get("modelVersion").isNull()
                ? result.get("modelVersion").asText() : null);
        vo.setStatus(result.path("status").asText("SUCCESS"));
        vo.setDegraded(!"SUCCESS".equalsIgnoreCase(vo.getStatus()));

        return vo;
    }

    /**
     * 获取评价的分析结果
     */
    private AiTraceContext startTrace(String scene, Long correlationId) {
        if (traceService == null) {
            return null;
        }
        try {
            return traceService.startTrace(null, null, null, scene);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void recordRequestReceived(AiTraceContext context, String responseType, int requestedCount) {
        if (context == null || traceService == null) return;
        traceService.updateStructuredConditions(context, Map.of("responseType", responseType,
                "requestedCount", requestedCount));
        completeStage(startStage(context, "REQUEST_RECEIVED", 1,
                        Map.of("responseType", responseType, "requestedCount", requestedCount)),
                Map.of("accepted", true), null, null, null, null);
    }

    private AiRequestTraceStage startStage(
            AiTraceContext context, String name, int attemptNo, Object input
    ) {
        if (context == null || traceService == null) return null;
        try {
            return traceService.startStage(context, name, input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void completeStage(AiRequestTraceStage stage, Object output, String provider,
                               String modelName, String modelVersion, String promptVersion) {
        if (stage == null || traceService == null) return;
        try {
            traceService.completeStage(stage, output, provider, modelName, modelVersion, promptVersion);
        } catch (Exception ignored) {
            // Trace persistence must not affect the business result.
        }
    }

    private void failStage(AiRequestTraceStage stage, String code, String message) {
        if (stage == null || traceService == null) return;
        try {
            traceService.failStage(stage, code, message);
        } catch (Exception ignored) {
            // Trace persistence must not affect the business result.
        }
    }

    private void finishSingleTrace(AiTraceContext context, ReviewAnalysisResultVO response) {
        if (context == null || traceService == null) return;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reviewId", response.getReviewId());
        summary.put("reviewAnalysisId", response.getReviewAnalysisId());
        summary.put("status", statusOf(response));
        summary.put("degraded", Boolean.TRUE.equals(response.getDegraded()));
        traceService.updateStructuredConditions(context, analysisSummary(response));
        if (Boolean.TRUE.equals(response.getDegraded())) {
            failTrace(context, "REVIEW_ANALYSIS_DEGRADED", "AI analysis returned a failed result", summary);
        } else {
            traceService.completeTrace(context, "SUCCESS", summary, "FASTAPI", response.getModelName(),
                    response.getModelVersion(), "review-analysis:v1");
        }
    }

    private void finishBatchTrace(AiTraceContext context, ReviewBatchAnalysisResultVO response,
                                  List<String> errors) {
        if (context == null || traceService == null) return;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("requestedCount", response.getRequestedCount());
        summary.put("successCount", response.getSuccessCount());
        summary.put("failedCount", response.getFailedCount());
        summary.put("skippedCount", response.getSkippedCount());
        summary.put("analysisIds", response.getAnalysisIds());
        if (!errors.isEmpty()) summary.put("errorSummary", errors.stream().limit(10).toList());
        if (response.getFailedCount() == 0) {
            traceService.completeTrace(context, "SUCCESS", summary, "FASTAPI", null, null, "review-analysis:v1");
        } else if (response.getSuccessCount() > 0 || response.getSkippedCount() > 0) {
            traceService.completeTrace(context, "FALLBACK", summary, "FASTAPI", null, null, "review-analysis:v1");
        } else {
            failTrace(context, "BATCH_REVIEW_ANALYSIS_FAILED", "No review analysis succeeded", summary);
        }
    }

    private void failTrace(AiTraceContext context, String code, String message, Object summary) {
        if (context == null || traceService == null) return;
        traceService.failTraceSafely(context, summary, code, message);
    }

    private void setTraceHeader(HttpServletResponse response, AiTraceContext context) {
        if (response != null && context != null) response.setHeader("X-Trace-Id", context.traceId());
    }

    private void validateAnalysisResult(JsonNode result) {
        if (result == null || result.path("reviewId").isMissingNode()
                || result.path("sentiment").isMissingNode() || result.path("confidence").isMissingNode()) {
            throw new IllegalStateException("Invalid review analysis response");
        }
    }

    private String promptVersion(JsonNode result) {
        return result == null ? "NOT_APPLICABLE" : result.path("promptVersion").asText("review-analysis:v1");
    }

    private String statusOf(ReviewAnalysisResultVO response) {
        return response == null || response.getStatus() == null ? "FAILED" : response.getStatus();
    }

    private Map<String, Object> analysisSummary(ReviewAnalysisResultVO response) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reviewId", response.getReviewId());
        summary.put("reviewAnalysisId", response.getReviewAnalysisId());
        summary.put("status", statusOf(response));
        summary.put("degraded", Boolean.TRUE.equals(response.getDegraded()));
        return summary;
    }

    private String safeError(Exception exception) {
        String message = exception == null ? "Unknown error" : exception.getMessage();
        return message == null ? "Unknown error" : message.replaceAll("[\\r\\n]+", " ").substring(0,
                Math.min(200, message.length()));
    }

    @GetMapping("/{reviewId}/analysis")
    public ApiResponse<ReviewAnalysis> getAnalysis(@PathVariable Long reviewId) {
        ReviewAnalysis analysis = reviewService.getAnalysis(reviewId);
        return analysis != null ? ApiResponse.success(analysis) : ApiResponse.notFound("该评价尚未分析");
    }

    /**
     * 批量触发评价分析（用于种子数据批量分析）
     */
    @PostMapping("/batch-analyze")
    public ApiResponse<ReviewBatchAnalysisResultVO> batchAnalyze(
            @RequestParam Long merchantId,
            HttpServletResponse servletResponse
    ) {
        Page<Review> page = reviewService.listByMerchant(merchantId, 1, 100);
        List<Review> reviews = page.getRecords() == null ? List.of() : page.getRecords();
        AiTraceContext context = startTrace("BATCH_REVIEW_ANALYSIS", merchantId);
        ReviewBatchAnalysisResultVO response = new ReviewBatchAnalysisResultVO();
        response.setTraceId(context == null ? null : context.traceId());
        response.setRequestedCount(reviews.size());
        recordRequestReceived(context, "BATCH_REVIEW_ANALYSIS", reviews.size());
        List<String> errors = new ArrayList<>();
        int attemptNo = 0;
        for (Review review : reviews) {
            attemptNo++;
            // 跳过已分析的
            if (reviewService.getAnalysis(review.getId()) != null) {
                response.setSkippedCount(response.getSkippedCount() + 1);
                continue;
            }
            try {
                ReviewAnalysisResultVO item = analyzeReview(review, context, attemptNo);
                if ("SUCCESS".equalsIgnoreCase(statusOf(item))) {
                    response.setSuccessCount(response.getSuccessCount() + 1);
                    if (item.getReviewAnalysisId() != null) {
                        response.getAnalysisIds().add(item.getReviewAnalysisId());
                    }
                } else {
                    response.setFailedCount(response.getFailedCount() + 1);
                    errors.add("reviewId=" + review.getId() + ": " + statusOf(item));
                }
            } catch (RuntimeException exception) {
                response.setFailedCount(response.getFailedCount() + 1);
                errors.add("reviewId=" + review.getId() + ": " + safeError(exception));
                if (context != null && traceService != null) {
                    traceService.failRunningStagesSafely(context,
                            "REVIEW_ANALYSIS_FAILED", exception.getMessage());
                }
                // 单条失败不影响批量
            }
        }
        finishBatchTrace(context, response, errors);
        setTraceHeader(servletResponse, context);
        return ApiResponse.success("Batch analysis completed", response);
    }

    // ==================== 标签统计与筛选（EPIC-01 Story 8） ====================

    /**
    * 获取某个商家的评价标签统计（公开接口，不需要登录）。
    *
    * 返回该商家所有公开评价中出现的标签，以及每个标签的：
    * - 正面/中性/负面评价数量
    * - 总评价数量
    *
    * 前端用这个数据渲染标签筛选栏，用户点击标签后调用
    * GET /api/reviews?merchantId=xxx&tagCode=TASTE_GOOD 来筛选评价。
    *
    * 只有至少关联一条公开评价的标签才会被返回，
    * 没有评价支撑的标签不会出现
    */
    @GetMapping("/tags")
    public ApiResponse<List<ReviewTagStatVO>> getMerchantReviewTags(
        @RequestParam Long merchantId
    ) {
    List<ReviewTagStatVO> tags = reviewService.getMerchantReviewTags(merchantId);
    return ApiResponse.success(tags);
    }

    /**
    * 获取单条评价关联的标签（供评价列表使用）。
    */
    @GetMapping("/{reviewId}/tags")
    public ApiResponse<List<TagRelationWithName>> getReviewTags(
        @PathVariable Long reviewId
    ) {
    List<TagRelationWithName> tags = reviewService.getReviewTags(reviewId);
    return ApiResponse.success(tags);
    }


    // ==================== 评论编辑与删除 ====================

    /**
     * 编辑评价（支持图片）—— 仅允许作者修改自己的评价。
     * 前端用 multipart/form-data 格式提交。
     */
    @PutMapping("/{reviewId}")
    public ApiResponse<ReviewSubmitResponse> edit(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestPart("request") ReviewSubmitRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        ReviewSubmitResponse response = reviewService.editReview(
                userId, reviewId, request,
                images != null ? images : List.of()
        );
        return ApiResponse.success(response);
    }

    /**
     * 编辑评价（简单版，仅文字和评分）—— 仅允许作者修改自己的评价。
     * 前端用 application/json 格式提交。
     */
    @PutMapping("/{reviewId}/simple")
    public ApiResponse<ReviewSubmitResponse> editSimple(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ReviewSubmitRequest request
    ) {
        ReviewSubmitResponse response = reviewService.editReviewSimple(
                userId, reviewId, request
        );
        return ApiResponse.success(response);
    }

    /**
     * 删除评价 —— 逻辑删除（标记 status = "DELETED"），不物理删除数据。
     */
    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> delete(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        reviewService.deleteReview(userId, reviewId);
        return ApiResponse.success("评价已删除", null);
    }

    /**
     * 商家回复评价 —— 仅允许商家回复自己店铺的评价。
     */
    @PostMapping("/{reviewId}/reply")
    public ApiResponse<ReviewReplyVO> reply(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long merchantId,
            @RequestBody Map<String, String> request
    ) {
        String replyContent = request.get("replyContent");
        if (replyContent == null || replyContent.isBlank()) {
            return ApiResponse.failure("INVALID_REQUEST", "回复内容不能为空");
        }
        ReviewReplyVO response = reviewService.replyReview(merchantId, reviewId, replyContent);
        return ApiResponse.success(response);
    }

    // ==================== 评价辅助回复（EPIC-02 故事7） ====================

    /**
     * 【评价辅助回复】生成 AI 回复建议草稿。
     *
     * 商家用户查看单条评价时，点击"生成回复建议"按钮调用此接口。
     * 系统根据评价评分自动选择回复策略（好评/差评），调用 AI 服务
     * 生成有针对性的回复内容，并以"草稿"形式保存。
     *
     * 业务规则：
     * - 评分 >= 4 → 好评策略（感谢 + 回应具体优点）
     * - 评分 <= 2 → 差评策略（道歉 + 问题说明 + 改进承诺）
     * - 已有活跃草稿时直接返回，不重复调用 AI（节省费用）
     * - AI 调用失败时返回明确错误，不覆盖已有草稿
     *
     * 请求示例：
     *   POST /api/reviews/1/reply-draft/generate
     *   Header: X-User-Id: 10  (商家用户ID)
     *
     * 响应示例：
     *   {
     *     "code": "SUCCESS",
     *     "data": {
     *       "id": 1,
     *       "reviewId": 1,
     *       "merchantId": 5,
     *       "generatedContent": "感谢您的支持和认可...",
     *       "editedContent": null,
     *       "strategy": "POSITIVE",
     *       "status": "DRAFT"
     *     }
     *   }
     */
    @PostMapping("/{reviewId}/reply-draft/generate")
    public ApiResponse<ReviewReplyDraftVO> generateReplyDraft(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long merchantMemberId,
            HttpServletResponse servletResponse
    ) {
        AiTraceContext context = traceService == null ? null
                : traceService.startTrace(null, null, merchantMemberId, "REVIEW_REPLY_GENERATION");
        try {
            ReviewReplyDraftVO draft = traceService == null
                    ? replyDraftService.generateDraft(merchantMemberId, reviewId)
                    : replyDraftService.generateDraft(merchantMemberId, reviewId, context);
            if (context != null) {
                draft.setTraceId(context.traceId());
                draft.setAiTraceId(context.traceId());
                servletResponse.setHeader("X-Trace-Id", context.traceId());
            }
            return ApiResponse.success(draft);
        } catch (RuntimeException exception) {
            if (context != null) traceService.failTraceSafely(context,
                    "REPLY_GENERATION_FAILED", exception.getMessage());
            if (context != null) servletResponse.setHeader("X-Trace-Id", context.traceId());
            throw exception;
        }
    }

    /**
     * 【评价辅助回复】编辑 AI 生成的草稿内容。
     *
     * 商家用户在发布前可以对 AI 生成内容进行修改。
     * 修改后的内容保存到 editedContent 字段，发布时优先使用。
     *
     * 请求示例：
     *   PUT /api/reviews/1/reply-draft
     *   Header: X-User-Id: 10
     *   Body: { "editedContent": "感谢您的反馈，我们会改进..." }
     */
    @PutMapping("/{reviewId}/reply-draft")
    public ApiResponse<ReviewReplyDraftVO> editReplyDraft(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long merchantMemberId,
            @RequestBody EditReplyDraftRequest request
    ) {
        if (request.getEditedContent() == null || request.getEditedContent().isBlank()) {
            return ApiResponse.failure("INVALID_REQUEST", "回复内容不能为空");
        }
        ReviewReplyDraftVO draft = replyDraftService.editDraft(
                merchantMemberId, reviewId, request.getEditedContent()
        );
        return ApiResponse.success(draft);
    }

    /**
     * 【评价辅助回复】发布草稿为正式商家回复。
     *
     * 商家确认回复内容后调用此接口，将草稿转为正式回复。
     * 正式回复写入 review_reply 表，用户端即可看到商家的回复。
     *
     * 根据验收准则4："未点击确认时回复不会进入已发布状态"
     *
     * 请求示例：
     *   POST /api/reviews/1/reply-draft/publish
     *   Header: X-User-Id: 10
     */
    @PostMapping("/{reviewId}/reply-draft/publish")
    public ApiResponse<ReviewReplyVO> publishReplyDraft(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long merchantMemberId
    ) {
        ReviewReplyVO reply = replyDraftService.publishDraft(merchantMemberId, reviewId);
        return ApiResponse.success(reply);
    }

    /**
     * 【评价辅助回复】获取当前草稿。
     *
     * 前端在加载评价详情时调用此接口，判断是否已有待处理的草稿。
     * 如果有草稿则展示草稿内容，如果没有则显示"生成回复建议"按钮。
     *
     * 请求示例：
     *   GET /api/reviews/1/reply-draft
     */
    @GetMapping("/{reviewId}/reply-draft")
    public ApiResponse<ReviewReplyDraftVO> getReplyDraft(
            @PathVariable Long reviewId
    ) {
        ReviewReplyDraftVO draft = replyDraftService.getDraft(reviewId);
        return ApiResponse.success(draft);
    }

    /**
     * 【评价辅助回复】丢弃草稿。
     *
     * 商家选择不使用 AI 生成的回复建议时调用此接口。
     * 丢弃后草稿状态变为 DISCARDED，商家可以重新请求生成新的回复建议。
     *
     * 请求示例：
     *   DELETE /api/reviews/1/reply-draft
     *   Header: X-User-Id: 10
     */
    @DeleteMapping("/{reviewId}/reply-draft")
    public ApiResponse<Void> discardReplyDraft(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long merchantMemberId
    ) {
        replyDraftService.discardDraft(merchantMemberId, reviewId);
        return ApiResponse.success("草稿已丢弃", null);
    }

    // ==================== 评价辅助回复 结束 ====================

    /**
     * 查询当前用户的评价列表（"我的评价"页面用）。
     */
    @GetMapping("/my-reviews")
    public ApiResponse<PageResult<MyReviewListVO>> myReviews(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer rating
    ) {
        Page<MyReviewListVO> page = reviewService.listMyReviews(
                userId, pageNum, pageSize, status, rating
        );
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 获取当前用户的评价详情（含图片、版本历史）。
     */
    @GetMapping("/my-reviews/{reviewId}")
    public ApiResponse<MyReviewDetailVO> myReviewDetail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long reviewId
    ) {
        MyReviewDetailVO detail = reviewService.getMyReviewDetail(userId, reviewId);
        return ApiResponse.success(detail);
    }

    // ==================== 评论编辑与删除 结束 ====================

    // ==================== 追评（追加评价）EPIC-08 故事2 ====================

    /**
     * 提交追评（追加评价）。
     *
     * 业务规则：
     * - 只能追评自己发表的、已发布的原评价
     * - 每条原评价最多追加一条追评
     * - 追评正文 10-2000 字符，消费日期必填，评分选填
     * - 不涉及图片上传
     *
     * 请求示例：
     *   POST /api/reviews/1/follow-up
     *   Body: { "content": "第二次来...", "rating": 5, "consumptionDate": "2026-07-18" }
     */
    @PostMapping("/{reviewId}/follow-up")
    public ApiResponse<ReviewSubmitResponse> submitFollowUp(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ReviewFollowUpRequest request
    ) {
        ReviewSubmitResponse response = reviewService.submitFollowUpReview(
                userId, reviewId, request
        );
        return ApiResponse.success(response);
    }

    /**
     * 编辑追评。
     *
     * 规则：
     * - 只能编辑自己写的追评
     * - 编辑保留历史版本
     * - 编辑后重新执行内容安全检测
     *
     * 请求示例：
     *   PUT /api/reviews/30/follow-up
     *   Body: { "content": "第三次来，体验更好了...", "rating": 5, "consumptionDate": "2026-07-20" }
     */
    @PutMapping("/{reviewId}/follow-up")
    public ApiResponse<ReviewSubmitResponse> editFollowUp(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ReviewFollowUpRequest request
    ) {
        ReviewSubmitResponse response = reviewService.editFollowUpReview(
                userId, reviewId, request
        );
        return ApiResponse.success(response);
    }

    /**
     * 删除追评 —— 逻辑删除。
     *
     * 根据 Jira EPIC-08 故事2 验收准则5：
     * "追评单独删除后原评价仍正常展示"
     *
     * 请求示例：
     *   DELETE /api/reviews/30/follow-up
     */
    @DeleteMapping("/{reviewId}/follow-up")
    public ApiResponse<Void> deleteFollowUp(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        reviewService.deleteFollowUpReview(userId, reviewId);
        return ApiResponse.success("追评已删除", null);
    }

    /**
     * 查询某条原评价的追评详情。
     *
     * 如果原评价存在有效追评，返回追评数据；否则返回 null。
     * 前端可通过此接口判断是否展示"追加评价"入口和追评内容。
     *
     * 请求示例：
     *   GET /api/reviews/1/follow-up
     */
    @GetMapping("/{reviewId}/follow-up")
    public ApiResponse<ReviewFollowUpVO> getFollowUp(
            @PathVariable Long reviewId
    ) {
        ReviewFollowUpVO followUp = reviewService.getFollowUpByParentId(reviewId);
        return ApiResponse.success(followUp);
    }

    // ==================== 追评（追加评价）结束 ====================

    // ==================== 差评归因分析（EPIC-02 Story 4） ====================

    /**
     * 获取商家差评归因统计。
     *
     * 返回指定时间范围内各类问题（卫生、服务、上菜速度等）的数量和占比。
     * 前端用这个数据渲染饼图/柱状图。
     *
     * 请求示例：
     *   GET /api/reviews/merchants/1/issue-stats?startDate=2026-06-01&endDate=2026-07-17
     */
    @GetMapping("/merchants/{merchantId}/issue-stats")
    public ApiResponse<List<IssueStatVO>> getMerchantIssueStats(
            @PathVariable Long merchantId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        List<IssueStatVO> stats = reviewService.getMerchantIssueStats(
                merchantId, startDate, endDate);
        return ApiResponse.success(stats);
    }

    /**
     * 获取某问题类别下的关联评价列表。
     *
     * 商家用户点击某个问题类别（如"上菜速度"）后，调用此接口查看
     * 被归因为该问题的原始评价及 AI 提取的依据片段。
     *
     * 请求示例：
     *   GET /api/reviews/merchants/1/issue-categories/SERVING_SPEED/reviews?pageNum=1&pageSize=10
     */
    @GetMapping("/merchants/{merchantId}/issue-categories/{categoryCode}/reviews")
    public ApiResponse<PageResult<IssueReviewVO>> getIssueCategoryReviews(
            @PathVariable Long merchantId,
            @PathVariable String categoryCode,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        Page<IssueReviewVO> page = reviewService.getIssueCategoryReviews(
                merchantId, categoryCode, pageNum, pageSize,
                startDate, endDate);
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 获取全部可用的问题类别字典。
     *
     * 前端用这个接口渲染类别筛选列表（保证和后端内置类别一致）。
     *
     * 请求示例：
     *   GET /api/reviews/issue-categories
     */
    @GetMapping("/issue-categories")
    public ApiResponse<List<ReviewIssueCategory>> getIssueCategories() {
        List<ReviewIssueCategory> categories = issueCategoryMapper.selectList(
                new LambdaQueryWrapper<ReviewIssueCategory>()
                        .eq(ReviewIssueCategory::getStatus, "ACTIVE")
                        .orderByAsc(ReviewIssueCategory::getId)
        );
        return ApiResponse.success(categories);
    }

    // ==================== 差评归因分析 结束 ====================

}
