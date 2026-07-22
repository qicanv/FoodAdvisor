package com.foodadvisor.dto.regression;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RegressionTestRunSetResponse(
        Long id,
        Long runId,
        Long testSetId,
        String testSetName,
        String testType,
        String status,

        String modelName,
        String modelVersion,
        String promptVersion,
        String algorithmVersion,
        String dataVersion,

        Integer requestedCaseCount,
        Integer completedCaseCount,
        Integer passedCount,
        Integer assertionFailedCount,
        Integer executionErrorCount,

        BigDecimal progressPercent,
        String errorMessage,

        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}