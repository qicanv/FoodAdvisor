package com.foodadvisor.backend.dto.modelconfig;

public record ConnectionTestResponse(
        boolean success,
        String message,
        Integer httpStatus
) {
}
