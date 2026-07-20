package com.foodadvisor.dto.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        long currentCount,
        long maxRequests,
        long retryAfterSeconds
) {
}
