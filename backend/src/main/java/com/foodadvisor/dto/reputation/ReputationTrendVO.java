package com.foodadvisor.dto.reputation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商家口碑趋势追踪 — 完整响应 VO。
 *
 * 包含统计概要和趋势数据点列表，前端可直接用于渲染趋势图。
 * 对应接口 GET /api/merchants/{merchantId}/reputation/trend
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReputationTrendVO {

    /** 商家 ID */
    private Long merchantId;

    /** 商家名称 */
    private String merchantName;

    /** 周期类型：DAY / WEEK / MONTH */
    private String periodType;

    /** 趋势数据点列表，按时间升序排列 */
    private List<ReputationTrendPointVO> dataPoints;

    // ---- 概要统计（基于当前查询范围内数据计算） ----

    /** 数据点数量 */
    private Integer totalPeriods;

    /** 范围内评价总数 */
    private Integer totalReviews;

    /** 范围内平均评分 */
    private BigDecimal overallAverageRating;

    /** 范围内正面评价占比 */
    private BigDecimal overallPositiveRatio;

    /** 范围内负面评价占比 */
    private BigDecimal overallNegativeRatio;

    /** 评分变化趋势：RISING（上升）/ STABLE（平稳）/ DECLINING（下降） */
    private String ratingTrend;

    /** 正面评价占比变化趋势 */
    private String sentimentTrend;
}
