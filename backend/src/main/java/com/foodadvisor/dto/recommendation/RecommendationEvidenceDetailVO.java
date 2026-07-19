package com.foodadvisor.dto.recommendation;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class RecommendationEvidenceDetailVO {
    private String sourceType;
    private Long merchantId;
    private String merchantName;
    private Long sourceId;
    private String excerpt;
    private OffsetDateTime reviewTime;
    private String highlightTitle;
    private Boolean available;
    private String unavailableReason;
}
