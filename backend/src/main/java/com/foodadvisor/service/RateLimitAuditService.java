package com.foodadvisor.service;

import com.foodadvisor.config.RateLimitKeyType;
import com.foodadvisor.config.RateLimitProperties;
import com.foodadvisor.dto.ratelimit.RateLimitDecision;
import com.foodadvisor.entity.RateLimitEvent;
import com.foodadvisor.mapper.RateLimitEventMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class RateLimitAuditService {
    private static final Logger log =
            LoggerFactory.getLogger(RateLimitAuditService.class);

    private final RateLimitEventMapper rateLimitEventMapper;

    public RateLimitAuditService(
            RateLimitEventMapper rateLimitEventMapper
    ) {
        this.rateLimitEventMapper = rateLimitEventMapper;
    }

    public void recordRejected(
            String ruleName,
            RateLimitProperties.Rule rule,
            String subjectValue,
            Long userId,
            String clientIp,
            HttpServletRequest request,
            RateLimitDecision decision
    ) {
        try {
            RateLimitEvent event = new RateLimitEvent();
            event.setRuleName(ruleName);
            RateLimitKeyType keyType = rule == null
                    ? null : rule.getKeyType();
            event.setKeyType(keyType == null ? null : keyType.name());
            event.setSubjectValue(subjectValue);
            event.setUserId(userId);
            event.setClientIp(clientIp);
            event.setRequestMethod(request == null
                    ? null : request.getMethod());
            event.setRequestPath(request == null
                    ? null : request.getRequestURI());
            event.setLimitCount(decision == null
                    ? null : Math.toIntExact(decision.maxRequests()));
            event.setWindowSeconds(rule == null
                    ? null : Math.toIntExact(rule.getWindowSeconds()));
            event.setCurrentCount(decision == null
                    ? null : decision.currentCount());
            event.setRetryAfterSeconds(decision == null
                    ? null : Math.toIntExact(decision.retryAfterSeconds()));
            event.setCreatedAt(OffsetDateTime.now());
            rateLimitEventMapper.insert(event);
        } catch (Exception exception) {
            log.warn(
                    "Rate limit audit write failed for rule={}: {}",
                    ruleName,
                    exception.getClass().getSimpleName()
            );
        }
    }
}
