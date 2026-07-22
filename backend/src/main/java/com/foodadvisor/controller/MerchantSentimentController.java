package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.sentiment.SentimentReviewPageVO;
import com.foodadvisor.dto.sentiment.SentimentSummaryVO;
import com.foodadvisor.service.MerchantSentimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商家评论情感分析接口。
 *
 * 提供商家端"评论情感分析"页面的后端数据：
 * - 汇总统计（情感分布、维度占比、关键词排名、差评归类）
 * - 评论明细列表（支持多条件筛选分页）
 * - 触发批量 AI 情感分析
 */
@RestController
public class MerchantSentimentController {

    private static final Logger log = LoggerFactory.getLogger(MerchantSentimentController.class);

    private final MerchantSentimentService sentimentService;

    public MerchantSentimentController(MerchantSentimentService sentimentService) {
        this.sentimentService = sentimentService;
    }

    /**
     * 获取评论情感分析汇总数据。
     *
     * 请求示例：
     *   GET /api/merchant-console/reviews/sentiment-summary?merchantId=1&timeRange=30d
     */
    @GetMapping("/api/merchant-console/reviews/sentiment-summary")
    public ApiResponse<SentimentSummaryVO> getSummary(
            @RequestParam Long merchantId,
            @RequestParam(defaultValue = "30d") String timeRange
    ) {
        log.info("情感分析汇总: merchantId={}, timeRange={}", merchantId, timeRange);
        SentimentSummaryVO summary = sentimentService.getSummary(merchantId, timeRange);
        return ApiResponse.success(summary);
    }

    /**
     * 获取评论情感分析明细列表（分页 + 多条件筛选）。
     *
     * 请求示例：
     *   GET /api/merchant-console/reviews/sentiment-list
     *       ?merchantId=1&timeRange=30d&sentiment=NEGATIVE&dimension=SERVICE&keyword=慢&page=1&pageSize=15
     */
    @GetMapping("/api/merchant-console/reviews/sentiment-list")
    public ApiResponse<SentimentReviewPageVO> getReviewList(
            @RequestParam Long merchantId,
            @RequestParam(defaultValue = "30d") String timeRange,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String dimension,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize
    ) {
        log.info("情感分析列表: merchantId={}, sentiment={}, dimension={}, keyword={}, page={}",
                merchantId, sentiment, dimension, keyword, page);
        SentimentReviewPageVO result = sentimentService.getReviewPage(
                merchantId, timeRange, sentiment, dimension, keyword, page, pageSize);
        return ApiResponse.success(result);
    }

    /**
     * 触发批量情感分析。
     *
     * 对指定时间范围内尚未分析的评论调用 AI 服务进行批量分析。
     *
     * 请求示例：
     *   POST /api/merchant-console/reviews/batch-analyze
     *   Body: { "merchantId": 1, "timeRange": "30d" }
     */
    @PostMapping("/api/merchant-console/reviews/batch-analyze")
    public ApiResponse<Map<String, Object>> triggerBatchAnalysis(
            @RequestBody Map<String, Object> body
    ) {
        Long merchantId = body.get("merchantId") != null
                ? Long.valueOf(body.get("merchantId").toString()) : null;
        String timeRange = body.get("timeRange") != null
                ? body.get("timeRange").toString() : "30d";

        if (merchantId == null) {
            return ApiResponse.failure("INVALID_PARAM", "merchantId 不能为空");
        }

        log.info("触发批量分析: merchantId={}, timeRange={}", merchantId, timeRange);
        Map<String, Object> result = sentimentService.triggerBatchAnalysis(merchantId, timeRange);
        return ApiResponse.success("批量分析完成", result);
    }
}
