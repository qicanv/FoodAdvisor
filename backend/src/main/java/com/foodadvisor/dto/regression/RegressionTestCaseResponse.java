package com.foodadvisor.dto.regression;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record RegressionTestCaseResponse(
        Long id,
        Long testSetId,
        String caseCode,
        String caseName,
        String description,
        JsonNode inputPayload,
        JsonNode expectedOutput,
        JsonNode assertionConfig,
        JsonNode tags,
        Integer sequenceNo,
        Boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}