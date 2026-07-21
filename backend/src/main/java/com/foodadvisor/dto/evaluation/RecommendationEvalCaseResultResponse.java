package com.foodadvisor.dto.evaluation;

import java.time.OffsetDateTime;

public record RecommendationEvalCaseResultResponse(
        Long id,
        Long runId,
        Long caseId,
        String status,
        String traceId,
        String inputSnapshot,
        String expectedConstraints,
        String extractedConstraints,
        String mergedConstraints,
        String recommendationSnapshot,
        String hardConditionMetrics,
        String failureReasons,
        Integer resultCount,
        Long durationMs,
        String errorMessage,
        String relevanceLabel,
        String annotationNote,
        Long annotatedBy,
        OffsetDateTime annotatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}