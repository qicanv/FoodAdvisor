package com.foodadvisor.dto.regression;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record RegressionTestCaseResultResponse(
        Long id,
        Long runId,
        Long runSetId,
        Long caseId,
        Long baselineResultId,

        String caseCode,
        String caseName,
        String testType,

        String executionStatus,
        String assertionStatus,
        String comparisonStatus,

        String modelName,
        String modelVersion,
        String promptVersion,
        String algorithmVersion,
        String dataVersion,

        JsonNode inputSnapshot,
        JsonNode expectedSnapshot,
        JsonNode actualOutput,
        JsonNode metrics,
        JsonNode failureReasons,

        String traceId,
        Long durationMs,
        String errorMessage,

        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}