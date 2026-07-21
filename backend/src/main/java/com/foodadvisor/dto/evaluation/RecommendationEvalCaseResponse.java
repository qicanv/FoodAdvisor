package com.foodadvisor.dto.evaluation;

import java.time.OffsetDateTime;

public record RecommendationEvalCaseResponse(
        Long id,
        Long datasetId,
        String caseCode,
        String caseName,
        String inputText,
        String expectedConstraints,
        String locationSnapshot,
        String tags,
        Integer sequenceNo,
        Boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}