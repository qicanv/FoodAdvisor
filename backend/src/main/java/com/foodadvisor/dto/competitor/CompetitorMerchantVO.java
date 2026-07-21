package com.foodadvisor.dto.competitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 竞品对比中的单家商家数据 — 从数据库查询的统计值。
 *
 * 包含本店和每家竞品的核心经营指标，
 * 所有数值均来自数据库真实统计，AI 只在此基础上生成文字分析。
 *
 * 验收准则对齐：
 * - AC-2: 至少包含价格、评分、好评率、评价数量 4 个维度
 * - AC-4: 数值与数据库查询结果一致
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorMerchantVO {

    // ---- 基本信息 ----
    /** 商家 ID */
    private Long merchantId;
    /** 商家名称 */
    private String merchantName;
    /** 商家类别（如 火锅、川菜、咖啡厅） */
    private String category;
    /** 菜系 */
    private String cuisine;
    /** 地址 */
    private String address;
    /** 是否为发起对比的本店 */
    private Boolean isSelf;

    // ---- 核心对比指标 ----
    /** 人均消费（元） */
    private BigDecimal averagePrice;
    /** 综合评分（0~5） */
    private BigDecimal rating;
    /** 评价总数 */
    private Integer reviewCount;
    /** 好评率（0~1），正面评价数 / 有效评价总数 */
    private BigDecimal positiveRate;
    /** 口味评分均值（0~5） */
    private BigDecimal tasteRating;
    /** 环境评分均值（0~5） */
    private BigDecimal environmentRating;
    /** 服务评分均值（0~5） */
    private BigDecimal serviceRating;

    // ---- 标签与问题 ----
    /** 高频正面标签（Top-5），如 ["口味好", "环境舒适", "服务热情"] */
    private List<String> topPositiveTags;
    /** 主要差评问题（Top-5），如 ["上菜慢", "排队久", "价格偏高"] */
    private List<String> topNegativeIssues;
}
