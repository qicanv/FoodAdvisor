package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 商家口碑统计表实体 —— 对应 merchant_reputation_statistics 表。
 *
 * 每条记录代表某个商家在某个统计周期（日/周/月）内的聚合口碑数据，
 * 包括平均评分、好中差评数量及占比，用于口碑趋势追踪功能。
 */
@Data
@TableName("merchant_reputation_statistics")
public class MerchantReputationStatistics {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商家 ID，关联 merchants 表 */
    private Long merchantId;

    /**
     * 统计周期类型：
     * DAY   — 按天统计
     * WEEK  — 按周统计（自然周，周一至周日）
     * MONTH — 按月统计（自然月）
     */
    private String periodType;

    /** 统计周期起始日期（含） */
    private LocalDate periodStart;

    /** 统计周期结束日期（含） */
    private LocalDate periodEnd;

    /** 该周期内所有已发布评价的平均综合评分 */
    private BigDecimal averageRating;

    /** 正面评价数量（情感为 POSITIVE 的评价） */
    private Integer positiveCount;

    /** 中性评价数量（情感为 NEUTRAL 的评价） */
    private Integer neutralCount;

    /** 负面评价数量（情感为 NEGATIVE 的评价） */
    private Integer negativeCount;

    /** 该周期内评价总数（PUBLISHED + APPROVED） */
    private Integer totalReviewCount;

    /** 正面评价占比 = positiveCount / totalReviewCount，范围 [0, 1] */
    private BigDecimal positiveRatio;

    /** 负面评价占比 = negativeCount / totalReviewCount，范围 [0, 1] */
    private BigDecimal negativeRatio;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
