package com.foodadvisor.backend.common;

public record ApiResponse<T>(
        String code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                "SUCCESS",
                "Request succeeded",
                data
        );
    }

    public static <T> ApiResponse<T> failure(
            String code,
            String message
    ) {
        return new ApiResponse<>(
                code,
                message,
                null
        );
    }

    public static <T> ApiResponse<T> notFound(String message) {
        return new ApiResponse<>(
                "NOT_FOUND",
                message,
                null
        );
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
                "SUCCESS",
                message,
                data
        );
    }
}