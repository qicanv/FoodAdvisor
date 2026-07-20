package com.foodadvisor.service;

import com.foodadvisor.config.RateLimitKeyType;
import com.foodadvisor.config.RateLimitProperties;
import com.foodadvisor.dto.ratelimit.RateLimitDecision;
import com.foodadvisor.entity.RateLimitEvent;
import com.foodadvisor.mapper.RateLimitEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RateLimitAuditServiceTest {

    @Mock
    private RateLimitEventMapper rateLimitEventMapper;

    private RateLimitAuditService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitAuditService(rateLimitEventMapper);
    }

    @Test
    void recordsRejectedEventWithoutSensitiveRequestData() {
        RateLimitProperties.Rule rule = rule();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/diner/sessions/1/messages");
        request.addHeader("Authorization", "Bearer secret");
        request.addHeader("Cookie", "session=secret");

        service.recordRejected(
                "dialogue-message-user",
                rule,
                "42",
                42L,
                "127.0.0.1",
                request,
                new RateLimitDecision(false, 11, 10, 21)
        );

        ArgumentCaptor<RateLimitEvent> captor =
                ArgumentCaptor.forClass(RateLimitEvent.class);
        verify(rateLimitEventMapper).insert(captor.capture());
        RateLimitEvent event = captor.getValue();

        assertAll(
                () -> assertEquals("dialogue-message-user",
                        event.getRuleName()),
                () -> assertEquals("USER", event.getKeyType()),
                () -> assertEquals("42", event.getSubjectValue()),
                () -> assertEquals(42L, event.getUserId()),
                () -> assertEquals("127.0.0.1", event.getClientIp()),
                () -> assertEquals("POST", event.getRequestMethod()),
                () -> assertEquals("/api/diner/sessions/1/messages",
                        event.getRequestPath()),
                () -> assertEquals(10, event.getLimitCount()),
                () -> assertEquals(60, event.getWindowSeconds()),
                () -> assertEquals(11L, event.getCurrentCount()),
                () -> assertEquals(21, event.getRetryAfterSeconds()),
                () -> assertNotNull(event.getCreatedAt())
        );
    }

    @Test
    void swallowsMapperFailure() {
        doThrow(new RuntimeException("database down"))
                .when(rateLimitEventMapper)
                .insert(org.mockito.ArgumentMatchers.any(
                        RateLimitEvent.class
                ));

        service.recordRejected(
                "dialogue-message-user",
                rule(),
                "42",
                42L,
                "127.0.0.1",
                new MockHttpServletRequest(),
                new RateLimitDecision(false, 11, 10, 21)
        );
    }

    private RateLimitProperties.Rule rule() {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setEnabled(true);
        rule.setPaths(List.of("/api/diner/sessions/*/messages"));
        rule.setMethods(List.of("POST"));
        rule.setKeyType(RateLimitKeyType.USER);
        rule.setMaxRequests(10);
        rule.setWindowSeconds(60);
        return rule;
    }
}
