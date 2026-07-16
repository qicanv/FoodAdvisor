package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.foodadvisor.backend.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.ReviewAnalysisResultVO;
import com.foodadvisor.dto.review.ReviewSubmitRequest;
import com.foodadvisor.dto.review.ReviewSubmitResponse;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewAnalysis;
import com.foodadvisor.service.AIClientService;
import com.foodadvisor.service.ReviewService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 评价接口 — 评论查询 & AI 分析
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final AIClientService aiClientService;

    public ReviewController(ReviewService reviewService, AIClientService aiClientService) {
        this.reviewService = reviewService;
        this.aiClientService = aiClientService;
    }

    /**
     * 按商家分页查询评价
     */
    @GetMapping
    public ApiResponse<PageResult<Review>> list(
            @RequestParam Long merchantId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<Review> page = reviewService.listByMerchant(merchantId, pageNum, pageSize);
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 触发单条评价的 AI 分析（调用 FastAPI）（V0.3 更新）
     */
    @PostMapping("/{reviewId}/analyze")
    public ApiResponse<ReviewAnalysisResultVO> analyze(@PathVariable Long reviewId) {
        Review review = reviewService.getById(reviewId);
        if (review == null) {
            return ApiResponse.notFound("评价不存在");
        }

        int reviewVersion = review.getCurrentVersion() != null ? review.getCurrentVersion() : 1;

        // 调用 FastAPI 分析
        JsonNode result = aiClientService.analyzeReview(
                review.getId(), review.getMerchantId(), review.getContent(), reviewVersion);

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
        analysis.setBusinessTraceId(result.has("businessTraceId")
                ? result.get("businessTraceId").asText() : null);
        analysis.setStatus(result.has("status") ? result.get("status").asText() : "SUCCESS");
        if (result.has("errorMessage") && !result.get("errorMessage").isNull()) {
            analysis.setErrorMessage(result.get("errorMessage").asText());
        }
        reviewService.saveAnalysis(analysis);

        // 构建返回 VO
        ReviewAnalysisResultVO vo = buildResultVO(review, result);
        return ApiResponse.success(vo);
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

        return vo;
    }

    /**
     * 获取评价的分析结果
     */
    @GetMapping("/{reviewId}/analysis")
    public ApiResponse<ReviewAnalysis> getAnalysis(@PathVariable Long reviewId) {
        ReviewAnalysis analysis = reviewService.getAnalysis(reviewId);
        return analysis != null ? ApiResponse.success(analysis) : ApiResponse.notFound("该评价尚未分析");
    }

    /**
     * 批量触发评价分析（用于种子数据批量分析）
     */
    @PostMapping("/batch-analyze")
    public ApiResponse<Integer> batchAnalyze(@RequestParam Long merchantId) {
        Page<Review> page = reviewService.listByMerchant(merchantId, 1, 100);
        int count = 0;
        for (Review review : page.getRecords()) {
            // 跳过已分析的
            if (reviewService.getAnalysis(review.getId()) != null) continue;
            try {
                analyze(review.getId());
                count++;
            } catch (Exception ignored) {
                // 单条失败不影响批量
            }
        }
        return ApiResponse.success("分析完成", count);
    }

    // ==================== 评论编辑与删除 ====================

    /**
     * 编辑评价 —— 仅允许作者修改自己的评价。
     * 前端用 multipart/form-data 格式提交（可能包含图片）。
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
     * 查询当前用户的评价列表（"我的评价"页面用）。
     */
    @GetMapping("/my-reviews")
    public ApiResponse<PageResult<Review>> myReviews(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status
    ) {
        Page<Review> page = reviewService.listByUser(
                userId, pageNum, pageSize, status
        );
        return ApiResponse.success(PageResult.from(page));
    }

    // ==================== 评论编辑与删除 结束 ====================
}
