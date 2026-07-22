package com.foodadvisor.dto.regression;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RegressionTestRunResponse(
        Long id,
        String runName,
        Long baselineRunId,
        String status,

        JsonNode modelVersions,
        JsonNode promptVersions,
        JsonNode algorithmVersions,
        JsonNode dataVersions,
        JsonNode requestSnapshot,

        Integer requestedSetCount,
        Integer completedSetCount,
        Integer requestedCaseCount,
        Integer completedCaseCount,

        Integer passedCount,
        Integer assertionFailedCount,
        Integer executionErrorCount,

        BigDecimal progressPercent,
        String errorMessage,

        Long createdBy,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}