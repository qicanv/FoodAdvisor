package com.foodadvisor.service;

import com.foodadvisor.config.RateLimitKeyType;
import com.foodadvisor.config.RateLimitProperties;
import com.foodadvisor.dto.ratelimit.RateLimitDecision;
import com.foodadvisor.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RateLimitService {
    private static final String FIXED_WINDOW_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {current, ttl}
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<List> fixedWindowScript;

    public RateLimitService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.fixedWindowScript = new DefaultRedisScript<>();
        this.fixedWindowScript.setScriptText(FIXED_WINDOW_SCRIPT);
        this.fixedWindowScript.setResultType(List.class);
    }

    public RateLimitDecision check(
            String ruleName,
            RateLimitProperties.Rule rule,
            String subjectValue
    ) {
        if (rule == null) {
            throw invalid("rate-limit rule is required");
        }
        return check(ruleName, rule.getKeyType(), subjectValue,
                rule.getMaxRequests(), rule.getWindowSeconds());
    }

    public RateLimitDecision check(
            String ruleName,
            RateLimitKeyType keyType,
            String subjectValue,
            int maxRequests,
            long windowSeconds
    ) {
        validate(ruleName, keyType, subjectValue, maxRequests, windowSeconds);
        String key = key(ruleName, keyType, subjectValue);
        List result;
        try {
            result = stringRedisTemplate.execute(
                    fixedWindowScript,
                    List.of(key),
                    String.valueOf(windowSeconds)
            );
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }

        if (result == null || result.size() < 2
                || !(result.get(0) instanceof Number)
                || !(result.get(1) instanceof Number)) {
            throw unavailable(null);
        }

        long currentCount = ((Number) result.get(0)).longValue();
        long ttl = ((Number) result.get(1)).longValue();
        boolean allowed = currentCount <= maxRequests;
        long retryAfterSeconds = allowed ? 0 : Math.max(1, ttl);
        return new RateLimitDecision(allowed, currentCount, maxRequests, retryAfterSeconds);
    }

    private String key(String ruleName, RateLimitKeyType keyType, String subjectValue) {
        return "rate-limit:" + ruleName.trim() + ":" + keyType + ":" + subjectValue.trim();
    }

    private void validate(
            String ruleName,
            RateLimitKeyType keyType,
            String subjectValue,
            int maxRequests,
            long windowSeconds
    ) {
        if (ruleName == null || ruleName.isBlank()) throw invalid("ruleName is required");
        if (keyType == null) throw invalid("keyType is required");
        if (subjectValue == null || subjectValue.isBlank()) throw invalid("subjectValue is required");
        if (maxRequests <= 0) throw invalid("maxRequests must be greater than zero");
        if (windowSeconds <= 0) throw invalid("windowSeconds must be greater than zero");
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RATE_LIMIT_REQUEST", message);
    }

    private ApiException unavailable(Exception cause) {
        String message = "Rate limit service is temporarily unavailable";
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "RATE_LIMIT_SERVICE_UNAVAILABLE", message);
    }
}
