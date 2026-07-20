package com.foodadvisor.trace;

import java.util.UUID;

public record AiTraceContext(
        String traceId,
        String requestId,
        Long sessionId,
        Long userId,
        String scene
) {
    public static AiTraceContext create(
            String requestId,
            Long sessionId,
            Long userId,
            String scene
    ) {
        return new AiTraceContext(
                "trc-" + UUID.randomUUID(),
                requestId == null || requestId.isBlank()
                        ? "req-" + UUID.randomUUID()
                        : requestId,
                sessionId,
                userId,
                scene
        );
    }
}
