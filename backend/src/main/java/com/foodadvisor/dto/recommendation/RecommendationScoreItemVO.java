package com.foodadvisor.dto.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单项推荐评分明细。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationScoreItemVO {

    /**
     * 该评分项的最高权重。
     */
    private BigDecimal weight;

    /**
     * 匹配系数，范围为0～1。
     */
    private BigDecimal factor;

    /**
     * 本项实际得分，范围为0～weight。
     */
    private BigDecimal score;

    /**
     * 本项得分的规则说明。
     */
    private String explanation;
}