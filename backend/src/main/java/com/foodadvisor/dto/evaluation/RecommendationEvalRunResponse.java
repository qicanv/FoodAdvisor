package com.foodadvisor.dto.evaluation;

import java.time.OffsetDateTime;

public record RecommendationEvalRunResponse(
        Long id,
        Long datasetId,
        String status,
        String modelName,
        String modelVersion,
        String promptVersion,
        String algorithmVersion,
        String dataVersion,
        Integer requestedCount,
        Integer successCount,
        Integer failedCount,
        Integer uniqueMerchantCount,
        String metrics,
        String errorMessage,
        Long createdBy,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}