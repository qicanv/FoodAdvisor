package com.foodadvisor.dto.reputation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 口碑趋势追踪 — 单个数据点 VO。
 *
 * 表示某个商家在某个统计周期内的口碑聚合指标，
 * 是趋势图（折线图/柱状图）中的一个数据点。
 *
 * 示例 JSON：
 * {
 *   "periodStart": "2026-06-01",
 *   "periodEnd": "2026-06-07",
 *   "periodType": "WEEK",
 *   "averageRating": 4.3,
 *   "totalReviewCount": 28,
 *   "positiveCount": 20,
 *   "neutralCount": 5,
 *   "negativeCount": 3,
 *   "positiveRatio": 0.7143,
 *   "negativeRatio": 0.1071
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReputationTrendPointVO {

    /** 统计周期起始日期 */
    private LocalDate periodStart;

    /** 统计周期结束日期 */
    private LocalDate periodEnd;

    /** 周期类型：DAY / WEEK / MONTH */
    private String periodType;

    /** 该周期内的平均综合评分 */
    private BigDecimal averageRating;

    /** 评价总数 */
    private Integer totalReviewCount;

    /** 正面评价数 */
    private Integer positiveCount;

    /** 中性评价数 */
    private Integer neutralCount;

    /** 负面评价数 */
    private Integer negativeCount;

    /** 正面评价占比 [0, 1] */
    private BigDecimal positiveRatio;

    /** 负面评价占比 [0, 1] */
    private BigDecimal negativeRatio;
}
