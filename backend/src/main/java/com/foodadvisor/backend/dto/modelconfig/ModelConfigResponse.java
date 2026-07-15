package com.foodadvisor.backend.dto.modelconfig;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ModelConfigResponse(
        Long id,
        String configName,
        String provider,
        String modelName,
        String baseUrl,
        String maskedApiKey,
        Integer timeoutMs,
        BigDecimal temperature,
        Integer maxOutputTokens,
        String status,
        String lastTestStatus,
        String lastTestMessage,
        OffsetDateTime lastTestedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
