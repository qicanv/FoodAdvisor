package com.foodadvisor.dto.evaluation;

import java.time.OffsetDateTime;

public record RecommendationEvalDatasetResponse(
        Long id,
        String name,
        String description,
        String dataVersion,
        String status,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}