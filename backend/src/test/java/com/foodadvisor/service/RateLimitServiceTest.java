package com.foodadvisor.service;

import com.foodadvisor.config.RateLimitKeyType;
import com.foodadvisor.dto.ratelimit.RateLimitDecision;
import com.foodadvisor.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService(stringRedisTemplate);
    }

    @Test
    void shouldAllowRequestsUpToConfiguredMaximum() {
        scriptReturns(List.of(1L, 60L), List.of(2L, 59L), List.of(3L, 58L));

        RateLimitDecision first = check("dialogue", "42", 3);
        RateLimitDecision second = check("dialogue", "42", 3);
        RateLimitDecision third = check("dialogue", "42", 3);

        assertAll(
                () -> assertTrue(first.allowed()),
                () -> assertTrue(second.allowed()),
                () -> assertTrue(third.allowed()),
                () -> assertEquals(3L, third.currentCount()),
                () -> assertEquals(0L, third.retryAfterSeconds())
        );
    }

    @Test
    void shouldRejectRequestAfterConfiguredMaximumAndReturnRetryAfter() {
        scriptReturns(List.of(4L, 37L));

        RateLimitDecision decision = check("dialogue", "42", 3);

        assertAll(
                () -> assertFalse(decision.allowed()),
                () -> assertEquals(4L, decision.currentCount()),
                () -> assertEquals(3L, decision.maxRequests()),
                () -> assertEquals(37L, decision.retryAfterSeconds())
        );
    }

    @Test
    void shouldUseDifferentKeysForRulesAndSubjects() {
        scriptReturns(List.of(1L, 60L), List.of(1L, 60L));

        check("dialogue", "42", 3);
        check("review-analysis", "43", 3);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(stringRedisTemplate, org.mockito.Mockito.times(2))
                .execute(any(DefaultRedisScript.class), keysCaptor.capture(), any());
        assertEquals("rate-limit:dialogue:USER:42", keysCaptor.getAllValues().get(0).get(0));
        assertEquals("rate-limit:review-analysis:USER:43", keysCaptor.getAllValues().get(1).get(0));
    }

    @Test
    void shouldExecuteFixedWindowLuaScriptWithWindowSeconds() {
        scriptReturns(List.of(1L, 60L));

        check("dialogue", "42", 3);

        ArgumentCaptor<DefaultRedisScript> scriptCaptor = ArgumentCaptor.forClass(DefaultRedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object> windowCaptor = ArgumentCaptor.forClass(Object.class);
        verify(stringRedisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), windowCaptor.capture());
        assertAll(
                () -> assertEquals("rate-limit:dialogue:USER:42", keysCaptor.getValue().get(0)),
                () -> assertEquals("60", windowCaptor.getValue()),
                () -> assertEquals(List.class, scriptCaptor.getValue().getResultType()),
                () -> assertTrue(scriptCaptor.getValue().getScriptAsString().contains("INCR")),
                () -> assertTrue(scriptCaptor.getValue().getScriptAsString().contains("EXPIRE")),
                () -> assertTrue(scriptCaptor.getValue().getScriptAsString().contains("TTL"))
        );
    }

    @Test
    void shouldConvertRedisFailuresToServiceUnavailable() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        ApiException exception = assertThrows(ApiException.class,
                () -> check("dialogue", "42", 3));

        assertAll(
                () -> assertEquals("RATE_LIMIT_SERVICE_UNAVAILABLE", exception.getCode()),
                () -> assertEquals(503, exception.getStatus().value())
        );
    }

    @Test
    void shouldRejectInvalidParametersBeforeCallingRedis() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.check("", RateLimitKeyType.USER, "42", 3, 60));

        assertAll(
                () -> assertEquals("INVALID_RATE_LIMIT_REQUEST", exception.getCode()),
                () -> assertEquals(400, exception.getStatus().value())
        );
    }

    private RateLimitDecision check(String ruleName, String subjectValue, int maxRequests) {
        return service.check(ruleName, RateLimitKeyType.USER, subjectValue, maxRequests, 60);
    }

    @SafeVarargs
    private final void scriptReturns(List<Object>... responses) {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(responses[0], java.util.Arrays.copyOfRange(responses, 1, responses.length));
    }
}
