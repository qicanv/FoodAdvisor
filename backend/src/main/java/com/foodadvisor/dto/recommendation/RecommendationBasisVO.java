package com.foodadvisor.dto.recommendation;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecommendationBasisVO {
    private Long evidenceId;
    private String sourceType;
    private Long sourceId;
    private Long merchantId;
    private String title;
    private String summary;
    private String matchedCondition;
    private BigDecimal relevanceScore;
    private Boolean available = true;
    private String unavailableReason;
}
