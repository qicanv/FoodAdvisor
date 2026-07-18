package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 区域热词实体
 *
 * 存储基于各区域商家评价自动生成的热门词汇，
 * 用于前端展示"大家都在聊什么"等区域热度洞察。
 *
 * 热词来源：
 * - AI_TAG：基于 AI 分析 review_tag_relations 聚合
 * - KEYWORD_EXTRACT：基于 review_analysis.keywords 字段聚合
 *
 * 热度计算 = 提及频次 × 时间衰减 × 情感权重
 */
@Data
@TableName("region_hot_words")
public class RegionHotWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 区域编码，如 "310100" */
    private String regionCode;

    /** 热词文本，如 "麻辣鲜香"、"排队久" */
    private String word;

    /** 分类：TASTE / SERVICE / ENVIRONMENT / PRICE / SPEED / GENERAL */
    private String category;

    /** 情感倾向：POSITIVE / NEUTRAL / NEGATIVE */
    private String sentiment;

    /** 热度分数 0.00 ~ 100.00 */
    private BigDecimal heatScore;

    /** 提及次数（同一评价中多次出现计多次） */
    private Integer mentionCount;

    /** 关联评价数（去重） */
    private Integer reviewCount;

    /** 关联商家数（去重） */
    private Integer merchantCount;

    /** 正面评价占比 0.00 ~ 1.00 */
    private BigDecimal positiveRatio;

    /** 数据来源：AI_TAG / KEYWORD_EXTRACT */
    private String sourceType;

    /** 统计周期：DAILY / WEEKLY / MONTHLY */
    private String periodType;

    /** 统计起始日期 */
    private LocalDate periodStart;

    /** 统计截止日期 */
    private LocalDate periodEnd;

    /** 生成批次版本号 */
    private Integer version;

    /** 状态：ACTIVE / OUTDATED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
