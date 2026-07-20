package com.foodadvisor.exception;

import com.foodadvisor.dto.ratelimit.RateLimitDecision;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {
    private final String ruleName;
    private final RateLimitDecision decision;

    public RateLimitExceededException(
            String ruleName,
            RateLimitDecision decision
    ) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please retry later."
        );
        this.ruleName = ruleName;
        this.decision = decision;
    }

    public String getRuleName() {
        return ruleName;
    }

    public RateLimitDecision getDecision() {
        return decision;
    }

    public long getRetryAfterSeconds() {
        return decision == null
                ? 1
                : Math.max(1, decision.retryAfterSeconds());
    }
}
