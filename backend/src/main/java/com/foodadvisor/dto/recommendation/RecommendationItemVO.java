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

    /**
     * 仅引用数据库和规则计算结果生成的推荐理由。
     */
    private String reason;
}
