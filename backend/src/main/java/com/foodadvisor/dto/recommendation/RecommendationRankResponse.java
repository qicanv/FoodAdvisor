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

    private String traceId;

    /**
     * recommendations 表生成的推荐记录 ID。
     */
    private Long recommendationId;

    private Long sessionId;

    private String requestId;

    /**
     * 本次实际采用的排序算法版本。
     */
    private String algorithmVersion;

    /**
     * 本次语义检索状态：
     * FULL / PARTIAL / UNAVAILABLE / SKIPPED。
     */
    private String semanticStatus;

    /**
     * 是否因部分或全部语义检索来源不可用而发生降级。
     */
    private Boolean degraded;

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
