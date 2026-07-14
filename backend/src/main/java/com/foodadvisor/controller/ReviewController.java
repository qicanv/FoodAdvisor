package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.foodadvisor.dto.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.ReviewAnalysisResultVO;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewAnalysis;
import com.foodadvisor.service.AIClientService;
import com.foodadvisor.service.ReviewService;
import org.springframework.web.bind.annotation.*;

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
     * 触发单条评价的 AI 分析（调用 FastAPI）
     */
    @PostMapping("/{reviewId}/analyze")
    public ApiResponse<ReviewAnalysisResultVO> analyze(@PathVariable Long reviewId) {
        Review review = reviewService.getById(reviewId);
        if (review == null) {
            return ApiResponse.notFound("评价不存在");
        }

        // 调用 FastAPI 分析
        JsonNode result = aiClientService.analyzeReview(
                review.getId(), review.getMerchantId(), review.getContent());

        // 保存分析结果到数据库
        ReviewAnalysis analysis = new ReviewAnalysis();
        analysis.setReviewId(review.getId());
        analysis.setSentiment(result.get("sentiment").asText());
        analysis.setConfidence(new BigDecimal(result.get("confidence").asText()));
        analysis.setKeywords(result.get("keywords").toString());
        analysis.setAspects(result.get("aspects").toString());
        if (result.has("negativeReason") && !result.get("negativeReason").isNull()) {
            analysis.setNegativeReason(result.get("negativeReason").asText());
        }
        analysis.setModelName(result.has("modelName") ? result.get("modelName").asText() : null);
        reviewService.saveAnalysis(analysis);

        // 构建返回结果
        ReviewAnalysisResultVO vo = new ReviewAnalysisResultVO();
        vo.setReviewId(review.getId());
        vo.setMerchantId(review.getMerchantId());
        vo.setSentiment(analysis.getSentiment());
        vo.setConfidence(analysis.getConfidence());

        // 解析 JSON 字段
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            vo.setKeywords(mapper.readValue(analysis.getKeywords(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
            vo.setAspects(mapper.readValue(analysis.getAspects(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<ReviewAnalysisResultVO.AspectVO>>() {}));
        } catch (Exception ignored) {
            vo.setKeywords(new ArrayList<>());
            vo.setAspects(new ArrayList<>());
        }
        vo.setNegativeReason(analysis.getNegativeReason());

        return ApiResponse.success(vo);
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
}
