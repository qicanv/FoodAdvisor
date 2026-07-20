package com.foodadvisor.config;

import com.foodadvisor.dto.ratelimit.RateLimitDecision;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.exception.RateLimitExceededException;
import com.foodadvisor.service.RateLimitAuditService;
import com.foodadvisor.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private static final Logger log =
            LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final RateLimitAuditService rateLimitAuditService;
    private final PathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitInterceptor(
            RateLimitProperties properties,
            RateLimitService rateLimitService,
            RateLimitAuditService rateLimitAuditService
    ) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.rateLimitAuditService = rateLimitAuditService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (properties == null || !properties.isEnabled()) {
            return true;
        }

        Map<String, RateLimitProperties.Rule> rules =
                properties.getRules();
        if (rules == null || rules.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, RateLimitProperties.Rule> entry
                : rules.entrySet()) {
            String ruleName = entry.getKey();
            RateLimitProperties.Rule rule = entry.getValue();
            if (!matches(request, rule)) {
                continue;
            }

            Long userId = extractUserId(request);
            String clientIp = clientIp(request);
            String subjectValue = subjectValue(rule, userId, clientIp);
            RateLimitDecision decision =
                    rateLimitService.check(ruleName, rule, subjectValue);
            if (!decision.allowed()) {
                try {
                    rateLimitAuditService.recordRejected(
                            ruleName,
                            rule,
                            subjectValue,
                            userId,
                            clientIp,
                            request,
                            decision
                    );
                } catch (Exception exception) {
                    log.warn(
                            "Rate limit audit service failed for rule={}: {}",
                            ruleName,
                            exception.getClass().getSimpleName()
                    );
                }
                throw new RateLimitExceededException(ruleName, decision);
            }
        }

        return true;
    }

    private boolean matches(
            HttpServletRequest request,
            RateLimitProperties.Rule rule
    ) {
        if (rule == null || !rule.isEnabled()) {
            return false;
        }
        if (!methodMatches(request.getMethod(), rule.getMethods())) {
            return false;
        }
        List<String> paths = rule.getPaths();
        if (paths == null || paths.isEmpty()) {
            return false;
        }
        String path = request.getRequestURI();
        return paths.stream()
                .filter(pattern -> pattern != null
                        && !pattern.isBlank())
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean methodMatches(String method, List<String> methods) {
        if (methods == null || methods.isEmpty()) {
            return true;
        }
        String actual = method == null
                ? "" : method.toUpperCase(Locale.ROOT);
        return methods.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(actual::equals);
    }

    private String subjectValue(
            RateLimitProperties.Rule rule,
            Long userId,
            String clientIp
    ) {
        RateLimitKeyType keyType = rule.getKeyType();
        if (keyType == RateLimitKeyType.IP) {
            return clientIp;
        }
        if (keyType == RateLimitKeyType.USER && userId != null) {
            return String.valueOf(userId);
        }
        throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication is required for user based rate limiting"
        );
    }

    private Long extractUserId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            log.warn(
                    "Ignoring invalid userId request attribute for rate limit"
            );
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank()
                ? "unknown"
                : remoteAddr;
    }
}
