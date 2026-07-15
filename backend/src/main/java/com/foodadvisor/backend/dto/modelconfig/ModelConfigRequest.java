package com.foodadvisor.backend.dto.modelconfig;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ModelConfigRequest(
        @NotBlank
        @Size(max = 100)
        String configName,

        @NotBlank
        @Size(max = 50)
        String provider,

        @NotBlank
        @Size(max = 100)
        String modelName,

        @NotBlank
        @Size(max = 500)
        String baseUrl,

        @Size(max = 500)
        String apiKey,

        @NotNull
        @Min(1000)
        @Max(120000)
        Integer timeoutMs,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("2.0")
        BigDecimal temperature,

        @NotNull
        @Min(1)
        @Max(32000)
        Integer maxOutputTokens,

        @NotBlank
        String status
) {
}
