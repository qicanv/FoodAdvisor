package com.foodadvisor.dto.recommendation;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个商家的推荐排序结果。
 */
@Data
public class RecommendationItemVO {

    /**
     * 本次推荐中的排名，从1开始。
     */
    private Integer rankNo;

    private Long merchantId;

    private String merchantName;

    private String category;

    private String cuisine;

    /**
     * 商家数据库综合评分。
     */
    private BigDecimal merchantRating;

    /**
     * 商家数据库人均价格。
     */
    private BigDecimal averagePrice;

    /**
     * 商家数据库评论数量。
     */
    private Integer reviewCount;

    /**
     * 商家当前真实营业状态。
     */
    private String operationStatus;

    /**
     * 根据用户位置和商家坐标计算的距离，单位为公里。
     * 未提供用户位置时为null。
     */
    private BigDecimal distanceKm;

    /**
     * 接口显示的综合得分，范围为0～100。
     */
    private BigDecimal finalScore;

    /**
     * 分项评分明细。
     *
     * LinkedHashMap 用于保证输出顺序稳定：
     * cuisine、rating、price、distance、environment、reputation。
     */
    private Map<String, RecommendationScoreItemVO> scoreItems =
            new LinkedHashMap<>();

    /**
     * 商家满足的主要条件。
     */
    private List<String> matchedConditions =
            new ArrayList<>();

    /**
     * 风险、数据缺失或未完全满足的条件。
     */
    private List<String> riskNotes =
            new ArrayList<>();

    private List<MatchedDishVO> matchedDishes =
            new ArrayList<>();

    private List<RecommendationBasisVO> recommendationBases =
            new ArrayList<>();

    /**
     * 语义匹配置信度 (0~1)。
     *
     * 基于来源多样性、命中数量和加权分数综合计算。
     * 低于 0.3 时前端可展示"匹配可信度较低"提示。
     */
    private BigDecimal semanticConfidence;

    /**
     * 语义匹配依据文本列表。
     *
     * 来自各路上匹配分数最高的 chunk 片段，格式为 "[来源] 文本"。
     */
    private List<String> semanticEvidence = new ArrayList<>();

    /**
     * 仅引用数据库和规则计算结果生成的推荐理由。
     */
    private String reason;

    /**
     * 商家经度（BD-09 坐标系）。
     */
    private BigDecimal longitude;

    /**
     * 商家纬度（BD-09 坐标系）。
     */
    private BigDecimal latitude;
}
