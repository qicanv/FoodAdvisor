package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.highlight.HighlightEvidenceVO;
import com.foodadvisor.dto.highlight.MerchantHighlightVO;
import com.foodadvisor.service.MerchantHighlightService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家亮点挖掘接口（EPIC-02 Story 5）
 *
 * 提供用户端亮点展示和商家端亮点生成/刷新两大能力。
 *
 * 用户端只读缓存结果，不触发模型调用；
 * 商家端可主动刷新，系统会根据新增评论数量判断是否需要调用 AI。
 */
@RestController
public class MerchantHighlightController {

    private final MerchantHighlightService highlightService;

    public MerchantHighlightController(
            MerchantHighlightService highlightService
    ) {
        this.highlightService = highlightService;
    }

    // ==================== 用户端（只读） ====================

    /**
     * 用户端：获取商家当前亮点列表。
     *
     * 只读缓存结果，不触发模型调用。
     * 返回列表中的每个元素代表一个亮点维度。
     *
     * 状态说明：
     * - 正常返回亮点列表（status=ACTIVE）
     * - 从未生成过（status=NONE）
     * - 正面评论不足（status=INSUFFICIENT_DATA）
     *
     * 请求示例：GET /api/merchants/1/highlights
     */
    @GetMapping("/api/merchants/{merchantId}/highlights")
    public ApiResponse<List<MerchantHighlightVO>> getHighlights(
            @PathVariable Long merchantId
    ) {
        return ApiResponse.success(
                highlightService.getDisplayHighlights(merchantId));
    }

    /**
     * 用户端：查看亮点依据（结论溯源到原始评价）。
     *
     * 每项亮点都可溯源到至少一条真实正面评论（验收准则 2/4）。
     *
     * 请求示例：
     *   GET /api/merchants/1/highlights/evidences?highlightId=5
     *
     * @param highlightId 可选，不传则返回该商家所有活跃亮点的依据
     */
    @GetMapping("/api/merchants/{merchantId}/highlights/evidences")
    public ApiResponse<List<HighlightEvidenceVO>> getEvidences(
            @PathVariable Long merchantId,
            @RequestParam(required = false) Long highlightId
    ) {
        return ApiResponse.success(
                highlightService.getHighlightEvidences(merchantId, highlightId));
    }

    // ==================== 商家端（生成/刷新） ====================

    /**
     * 商家端：生成或刷新商家亮点。
     *
     * 未达刷新条件（新增正面评论不足且未过期）时直接返回已有亮点，
     * force=true 可强制重新生成。
     *
     * 触发时机建议：
     * - 商家用户手动点击"刷新亮点"按钮
     * - 新增正面评论超过阈值后系统提示可刷新
     *
     * 请求示例：
     *   POST /api/merchant-console/merchants/1/highlights/generate
     *   POST /api/merchant-console/merchants/1/highlights/generate?force=true
     */
    @PostMapping("/api/merchant-console/merchants/{merchantId}/highlights/generate")
    public ApiResponse<List<MerchantHighlightVO>> generate(
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        List<MerchantHighlightVO> highlights =
                highlightService.generateHighlights(merchantId, force);

        // 判断是否为空状态
        if (highlights.size() == 1) {
            String status = highlights.get(0).getStatus();
            if ("INSUFFICIENT_DATA".equals(status)) {
                return ApiResponse.success(
                        "正面评论数量不足，无法生成亮点",
                        highlights);
            }
            if ("NONE".equals(status)) {
                return ApiResponse.success(
                        "未生成亮点",
                        highlights);
            }
        }

        return ApiResponse.success(
                "亮点生成完成",
                highlights);
    }
}
