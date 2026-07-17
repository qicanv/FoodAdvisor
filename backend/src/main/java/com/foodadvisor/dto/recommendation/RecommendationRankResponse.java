package com.foodadvisor.dto.recommendation;

import com.foodadvisor.dto.constraint.ConstraintState;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐排序响应。
 */
@Data
public class RecommendationRankResponse {

    /**
     * recommendations表生成的推荐记录ID。
     */
    private Long recommendationId;

    private Long sessionId;

    private String requestId;

    /**
     * 排序算法版本。
     */
    private String algorithmVersion;

    private Boolean matched;

    private String status;

    private String message;

    /**
     * 本次使用的会话约束快照。
     */
    private ConstraintState constraints;

    private ConstraintState currentConstraints;

    /**
     * 本次实际使用的权重快照。
     */
    private RecommendationWeights weights;

    private Integer resultCount;

    /**
     * 已按照最终得分从高到低排序的结果。
     */
    private List<RecommendationItemVO> results =
            new ArrayList<>();

    private List<LimitingConditionVO> limitingConditions =
            new ArrayList<>();

    private List<AdjustmentSuggestionVO> adjustmentSuggestions =
            new ArrayList<>();
}
