package com.foodadvisor.config;

import com.foodadvisor.dto.ratelimit.RateLimitDecision;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.exception.RateLimitExceededException;
import com.foodadvisor.service.RateLimitAuditService;
import com.foodadvisor.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private RateLimitAuditService rateLimitAuditService;

    private RateLimitProperties properties;
    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setRules(new LinkedHashMap<>());
        interceptor = new RateLimitInterceptor(
                new ObjectProvider<>() {
                    @Override
                    public RateLimitProperties getObject() { return properties; }
                    @Override
                    public RateLimitProperties getIfAvailable() { return properties; }
                    @Override
                    public RateLimitProperties getIfUnique() { return properties; }
                    @Override
                    public RateLimitProperties getObject(Object... args) { return properties; }
                },
                new ObjectProvider<>() {
                    @Override
                    public RateLimitService getObject() { return rateLimitService; }
                    @Override
                    public RateLimitService getIfAvailable() { return rateLimitService; }
                    @Override
                    public RateLimitService getIfUnique() { return rateLimitService; }
                    @Override
                    public RateLimitService getObject(Object... args) { return rateLimitService; }
                },
                new ObjectProvider<>() {
                    @Override
                    public RateLimitAuditService getObject() { return rateLimitAuditService; }
                    @Override
                    public RateLimitAuditService getIfAvailable() { return rateLimitAuditService; }
                    @Override
                    public RateLimitAuditService getIfUnique() { return rateLimitAuditService; }
                    @Override
                    public RateLimitAuditService getObject(Object... args) { return rateLimitAuditService; }
                }
        );
    }

    @Test
    void allowsWhenGlobalSwitchDisabled() {
        properties.setEnabled(false);
        properties.getRules().put("dialogue", userRule());

        assertTrue(interceptor.preHandle(request(), response(), new Object()));

        verifyNoInteractions(rateLimitService, rateLimitAuditService);
    }

    @Test
    void allowsWhenRuleDisabled() {
        RateLimitProperties.Rule rule = userRule();
        rule.setEnabled(false);
        properties.getRules().put("dialogue", rule);

        assertTrue(interceptor.preHandle(request(), response(), new Object()));

        verifyNoInteractions(rateLimitService, rateLimitAuditService);
    }

    @Test
    void allowsWhenPathDoesNotMatch() {
        properties.getRules().put("dialogue", userRule());
        MockHttpServletRequest request = request();
        request.setRequestURI("/api/diner/sessions/1/messages/history");

        assertTrue(interceptor.preHandle(request, response(), new Object()));

        verifyNoInteractions(rateLimitService, rateLimitAuditService);
    }

    @Test
    void allowsWhenMethodDoesNotMatch() {
        properties.getRules().put("dialogue", userRule());
        MockHttpServletRequest request = request();
        request.setMethod("GET");

        assertTrue(interceptor.preHandle(request, response(), new Object()));

        verifyNoInteractions(rateLimitService, rateLimitAuditService);
    }

    @Test
    void readsUserIdAttributeForUserRule() {
        RateLimitProperties.Rule rule = userRule();
        properties.getRules().put("dialogue", rule);
        when(rateLimitService.check("dialogue", rule, "42"))
                .thenReturn(allowed());

        assertTrue(interceptor.preHandle(request(), response(), new Object()));

        verify(rateLimitService).check("dialogue", rule, "42");
    }

    @Test
    void readsRemoteAddressForIpRuleWithoutTrustingForwardedFor() {
        RateLimitProperties.Rule rule = ipRule();
        properties.getRules().put("dialogue-ip", rule);
        when(rateLimitService.check("dialogue-ip", rule, "10.0.0.8"))
                .thenReturn(allowed());
        MockHttpServletRequest request = request();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.77");

        assertTrue(interceptor.preHandle(request, response(), new Object()));

        verify(rateLimitService).check("dialogue-ip", rule, "10.0.0.8");
    }

    @Test
    void allowsWithinQuotaAndDoesNotAudit() {
        RateLimitProperties.Rule rule = userRule();
        properties.getRules().put("dialogue", rule);
        when(rateLimitService.check("dialogue", rule, "42"))
                .thenReturn(allowed());

        assertTrue(interceptor.preHandle(request(), response(), new Object()));

        verify(rateLimitAuditService, never()).recordRejected(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsWhenQuotaExceededAndAudits() {
        RateLimitProperties.Rule rule = userRule();
        properties.getRules().put("dialogue", rule);
        RateLimitDecision denied = denied();
        when(rateLimitService.check("dialogue", rule, "42"))
                .thenReturn(denied);

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> interceptor.preHandle(request(), response(),
                        new Object())
        );

        assertAll(
                () -> assertEquals("RATE_LIMIT_EXCEEDED",
                        exception.getCode()),
                () -> assertEquals(13, exception.getRetryAfterSeconds())
        );
        verify(rateLimitAuditService).recordRejected(
                eq("dialogue"),
                eq(rule),
                eq("42"),
                eq(42L),
                eq("127.0.0.1"),
                any(),
                eq(denied)
        );
    }

    @Test
    void retryAfterIsNeverLessThanOneSecond() {
        RateLimitExceededException exception =
                new RateLimitExceededException(
                        "dialogue",
                        new RateLimitDecision(false, 11, 10, 0)
                );

        assertEquals(1, exception.getRetryAfterSeconds());
    }

    @Test
    void keepsRateLimitExceptionWhenAuditFails() {
        RateLimitProperties.Rule rule = userRule();
        properties.getRules().put("dialogue", rule);
        when(rateLimitService.check("dialogue", rule, "42"))
                .thenReturn(denied());
        doThrow(new RuntimeException("database unavailable"))
                .when(rateLimitAuditService)
                .recordRejected(any(), any(), any(), any(), any(),
                        any(), any());

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> interceptor.preHandle(request(), response(),
                        new Object())
        );

        assertEquals("RATE_LIMIT_EXCEEDED", exception.getCode());
    }

    @Test
    void doesNotBuildNullSubjectForUserRuleWithoutUserId() {
        properties.getRules().put("dialogue", userRule());
        MockHttpServletRequest request = request();
        request.removeAttribute("userId");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> interceptor.preHandle(request, response(),
                        new Object())
        );

        assertAll(
                () -> assertEquals("UNAUTHORIZED", exception.getCode()),
                () -> assertInstanceOf(ApiException.class, exception)
        );
        verifyNoInteractions(rateLimitService, rateLimitAuditService);
    }

    @Test
    void rejectsWhenAnyMatchingRuleRejects() {
        RateLimitProperties.Rule first = userRule();
        RateLimitProperties.Rule second = userRule();
        properties.setRules(new LinkedHashMap<>(Map.of()));
        properties.getRules().put("dialogue-soft", first);
        properties.getRules().put("dialogue-hard", second);
        when(rateLimitService.check("dialogue-soft", first, "42"))
                .thenReturn(allowed());
        when(rateLimitService.check("dialogue-hard", second, "42"))
                .thenReturn(denied());

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> interceptor.preHandle(request(), response(),
                        new Object())
        );

        assertEquals("dialogue-hard", exception.getRuleName());
    }

    @Test
    void matchesAllMethodsWhenMethodsAreEmpty() {
        RateLimitProperties.Rule rule = userRule();
        rule.setMethods(List.of());
        properties.getRules().put("dialogue", rule);
        MockHttpServletRequest request = request();
        request.setMethod("PATCH");
        when(rateLimitService.check("dialogue", rule, "42"))
                .thenReturn(allowed());

        assertTrue(interceptor.preHandle(request, response(), new Object()));

        verify(rateLimitService).check("dialogue", rule, "42");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/diner/sessions/1/messages");
        request.setRemoteAddr("127.0.0.1");
        request.setAttribute("userId", 42L);
        return request;
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }

    private RateLimitProperties.Rule userRule() {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setEnabled(true);
        rule.setPaths(List.of("/api/diner/sessions/*/messages"));
        rule.setMethods(List.of("POST"));
        rule.setKeyType(RateLimitKeyType.USER);
        rule.setMaxRequests(10);
        rule.setWindowSeconds(60);
        return rule;
    }

    private RateLimitProperties.Rule ipRule() {
        RateLimitProperties.Rule rule = userRule();
        rule.setKeyType(RateLimitKeyType.IP);
        return rule;
    }

    private RateLimitDecision allowed() {
        return new RateLimitDecision(true, 1, 10, 0);
    }

    private RateLimitDecision denied() {
        return new RateLimitDecision(false, 11, 10, 13);
    }
}
