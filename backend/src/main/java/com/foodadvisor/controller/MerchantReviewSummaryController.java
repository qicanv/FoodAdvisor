package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.summary.MerchantReviewSummaryVO;
import com.foodadvisor.dto.summary.SummaryEvidenceVO;
import com.foodadvisor.service.MerchantReviewSummaryService;
import com.foodadvisor.service.AiRequestTraceService;
import com.foodadvisor.trace.AiTraceContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家评价智能总结接口（EPIC-01 Story 7）
 */
@RestController
public class MerchantReviewSummaryController {

    private final MerchantReviewSummaryService summaryService;
    private final AiRequestTraceService traceService;

    public MerchantReviewSummaryController(
            MerchantReviewSummaryService summaryService
    ) {
        this(summaryService, null);
    }

    @Autowired
    public MerchantReviewSummaryController(
            MerchantReviewSummaryService summaryService,
            AiRequestTraceService traceService
    ) {
        this.summaryService = summaryService;
        this.traceService = traceService;
    }

    /**
     * 用户端：获取商家当前评价摘要。
     *
     * 只读缓存结果，不触发模型调用（验收准则 7）。
     * status 说明：
     * - SUCCESS            正常摘要
     * - INSUFFICIENT_DATA  评论数量不足，前端显示"评论数量不足"
     * - NONE               从未生成过摘要
     *
     * 请求示例：GET /api/merchants/1/review-summary
     */
    @GetMapping("/api/merchants/{merchantId}/review-summary")
    public ApiResponse<MerchantReviewSummaryVO> getSummary(
            @PathVariable Long merchantId
    ) {
        return ApiResponse.success(
                summaryService.getDisplaySummary(merchantId));
    }

    /**
     * 用户端：查看摘要依据（结论溯源到原始评价）。
     *
     * 请求示例：
     *   GET /api/merchants/1/review-summary/evidences?evidenceType=DISADVANTAGE
     */
    @GetMapping("/api/merchants/{merchantId}/review-summary/evidences")
    public ApiResponse<List<SummaryEvidenceVO>> getEvidences(
            @PathVariable Long merchantId,
            @RequestParam(required = false) Long summaryId,
            @RequestParam(required = false) String evidenceType
    ) {
        return ApiResponse.success(
                summaryService.getEvidences(merchantId, summaryId, evidenceType));
    }

    /**
     * 商家端：生成或刷新评价摘要。
     *
     * 未达刷新条件（新增评论不足且未过期）时直接返回已有摘要，
     * force=true 可强制重新生成。
     *
     * 请求示例：
     *   POST /api/merchant-console/merchants/1/review-summary/generate
     */
    @PostMapping("/api/merchant-console/merchants/{merchantId}/review-summary/generate")
    public ApiResponse<MerchantReviewSummaryVO> generate(
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "false") boolean force,
            HttpServletResponse servletResponse
    ) {
        AiTraceContext context = traceService == null ? null
                : traceService.startTrace(null, null, null, "MERCHANT_REVIEW_SUMMARY");
        try {
            MerchantReviewSummaryVO summary = traceService == null
                    ? summaryService.generateSummary(merchantId, force)
                    : summaryService.generateSummary(merchantId, force, context);
            if (context != null) {
                summary.setTraceId(context.traceId());
                servletResponse.setHeader("X-Trace-Id", context.traceId());
            }
            return ApiResponse.success("摘要生成完成", summary);
        } catch (RuntimeException exception) {
            if (context != null) traceService.failTraceSafely(
                    context, "SUMMARY_GENERATION_FAILED", exception.getMessage());
            if (context != null) servletResponse.setHeader("X-Trace-Id", context.traceId());
            throw exception;
        }
    }
}
