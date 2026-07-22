package com.foodadvisor.dto.regression;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record RegressionTestSetResponse(
        Long id,
        String name,
        String description,
        String testType,
        String dataVersion,
        String status,
        JsonNode metadata,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}