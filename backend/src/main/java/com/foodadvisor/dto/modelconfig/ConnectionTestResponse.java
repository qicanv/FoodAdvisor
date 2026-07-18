package com.foodadvisor.dto.modelconfig;

public record ConnectionTestResponse(
        boolean success,
        String message,
        Integer httpStatus
) {
}
