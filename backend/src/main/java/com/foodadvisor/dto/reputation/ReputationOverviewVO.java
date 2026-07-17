package com.foodadvisor.dto.reputation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 商家口碑总览 VO。
 *
 * 提供商家当前口碑的概览信息，包括最新评分、近期趋势判断等，
 * 用于商家口碑仪表盘顶部摘要区域。
 * 对应接口 GET /api/merchants/{merchantId}/reputation/overview
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReputationOverviewVO {

    /** 商家 ID */
    private Long merchantId;

    /** 商家名称 */
    private String merchantName;

    /** 当前综合评分（来自 merchants 表） */
    private BigDecimal currentRating;

    /** 评价总数（来自 merchants 表） */
    private Integer reviewCount;

    // ---- 近 30 天统计 ----

    /** 近 30 天平均评分 */
    private BigDecimal recent30dAvgRating;

    /** 近 30 天评价数量 */
    private Integer recent30dReviewCount;

    /** 近 30 天正面评价占比 */
    private BigDecimal recent30dPositiveRatio;

    /** 近 30 天负面评价占比 */
    private BigDecimal recent30dNegativeRatio;

    // ---- 与前 30 天的对比 ----

    /** 评分变化量（近 30 天 - 前 30 天），正数表示口碑上升 */
    private BigDecimal ratingChange;

    /** 正面评价占比变化量 */
    private BigDecimal positiveRatioChange;

    /** 负面评价占比变化量 */
    private BigDecimal negativeRatioChange;

    /** 口碑趋势：IMPROVING / STABLE / DECLINING */
    private String overallTrend;

    // ---- 最近有数据的日期 ----

    /** 最新一条统计数据的日期 */
    private LocalDate lastDataDate;
}
