package com.foodadvisor.controller;

import com.foodadvisor.backend.common.ApiResponse;
import com.foodadvisor.dto.reputation.ReputationOverviewVO;
import com.foodadvisor.dto.reputation.ReputationTrendVO;
import com.foodadvisor.service.MerchantReputationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 商家口碑趋势追踪接口。
 *
 * 提供商家口碑数据的趋势查询、概览查看和统计刷新功能。
 * 所有接口围绕 merchant_reputation_statistics 表展开，
 * 该表预聚合了按日/周/月维度的商家评价数据。
 *
 * 接口列表：
 * - GET  /api/merchants/{merchantId}/reputation/trend    口碑趋势查询
 * - GET  /api/merchants/{merchantId}/reputation/overview  口碑概览
 * - POST /api/merchants/{merchantId}/reputation/refresh   手动刷新统计
 */
@RestController
@RequestMapping("/api/merchants/{merchantId}/reputation")
public class MerchantReputationController {

    private final MerchantReputationService reputationService;

    public MerchantReputationController(MerchantReputationService reputationService) {
        this.reputationService = reputationService;
    }

    /**
     * 查询商家口碑趋势数据。
     *
     * 返回指定周期类型下的时间序列数据，每个数据点包含：
     * - 周期起止日期
     * - 平均评分
     * - 正面/中性/负面评价数量和占比
     *
     * 请求示例：
     * GET /api/merchants/1/reputation/trend?periodType=WEEK&startDate=2026-01-01&endDate=2026-06-30
     *
     * @param merchantId 商家 ID（路径参数）
     * @param periodType 周期类型：DAY / WEEK / MONTH，默认 WEEK
     * @param startDate  查询起始日期（含），默认 12 个周期前
     * @param endDate    查询结束日期（含），默认今天
     * @return 包含趋势数据点和概要信息的完整响应
     */
    @GetMapping("/trend")
    public ApiResponse<ReputationTrendVO> getTrend(
            @PathVariable Long merchantId,
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        ReputationTrendVO trend = reputationService.getReputationTrend(
                merchantId, periodType, startDate, endDate
        );
        return ApiResponse.success(trend);
    }

    /**
     * 获取商家口碑概览。
     *
     * 返回当前评分、近 30 天统计以及与前一周期（30 天前 ~ 60 天前）的环比变化，
     * 用于口碑仪表盘顶部摘要卡片。
     *
     * 请求示例：
     * GET /api/merchants/1/reputation/overview
     *
     * @param merchantId 商家 ID（路径参数）
     * @return 口碑概览数据
     */
    @GetMapping("/overview")
    public ApiResponse<ReputationOverviewVO> getOverview(
            @PathVariable Long merchantId
    ) {
        ReputationOverviewVO overview = reputationService.getReputationOverview(merchantId);
        if (overview == null) {
            return ApiResponse.notFound("商家不存在");
        }
        return ApiResponse.success(overview);
    }

    /**
     * 手动刷新商家口碑统计。
     *
     * 重新计算指定范围内指定周期类型的统计数据。
     * 采用"先删后插"策略保证幂等性，可重复调用。
     *
     * 请求示例：
     * POST /api/merchants/1/reputation/refresh?periodType=DAY&startDate=2026-01-01&endDate=2026-06-30
     *
     * @param merchantId 商家 ID（路径参数）
     * @param periodType 周期类型，默认 DAY
     * @param startDate  刷新起始日期，默认 90 天前
     * @param endDate    刷新结束日期，默认今天
     * @return 操作结果消息
     */
    @PostMapping("/refresh")
    public ApiResponse<String> refresh(
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "DAY") String periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(90);
        }

        reputationService.refreshReputationStats(merchantId, periodType, startDate, endDate);
        return ApiResponse.success("口碑统计数据已刷新");
    }
}
