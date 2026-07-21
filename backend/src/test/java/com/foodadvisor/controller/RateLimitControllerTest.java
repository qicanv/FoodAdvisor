package com.foodadvisor.controller;

import com.foodadvisor.config.JwtInterceptor;
import com.foodadvisor.config.RateLimitInterceptor;
import com.foodadvisor.config.RateLimitKeyType;
import com.foodadvisor.config.RateLimitProperties;
import com.foodadvisor.dto.constraint.ConstraintExtractResponse;
import com.foodadvisor.dto.ratelimit.RateLimitDecision;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.service.ConstraintExtractionService;
import com.foodadvisor.service.RateLimitAuditService;
import com.foodadvisor.service.RateLimitService;
import com.foodadvisor.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RateLimitControllerTest {

    @Mock
    private ConstraintExtractionService constraintExtractionService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private RateLimitAuditService rateLimitAuditService;

    private MockMvc mockMvc;
    private RateLimitProperties.Rule rule;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setRules(new LinkedHashMap<>());
        rule = rule();
        properties.getRules().put("constraint-extract-user", rule);
        RateLimitInterceptor rateLimitInterceptor =
                new RateLimitInterceptor(
                        providerOf(properties),
                        providerOf(rateLimitService),
                        providerOf(rateLimitAuditService)
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ConstraintController(
                                constraintExtractionService
                        )
                )
                .addInterceptors(
                        new JwtInterceptor(),
                        rateLimitInterceptor
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returns429AndDoesNotInvokeControllerAfterQuotaExceeded()
            throws Exception {
        ConstraintExtractResponse response =
                new ConstraintExtractResponse();
        response.setTraceId("trc-ok");
        when(constraintExtractionService.extractAndMerge(
                1L,
                42L,
                "想吃川菜"
        )).thenReturn(response);
        when(rateLimitService.check(
                "constraint-extract-user",
                rule,
                "42"
        )).thenReturn(
                new RateLimitDecision(true, 1, 2, 0),
                new RateLimitDecision(true, 2, 2, 0),
                new RateLimitDecision(false, 3, 2, 17)
        );

        mockMvc.perform(request())
                .andExpect(status().isOk());
        mockMvc.perform(request())
                .andExpect(status().isOk());
        mockMvc.perform(request())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(
                        HttpHeaders.RETRY_AFTER,
                        "17"
                ))
                .andExpect(jsonPath("$.code")
                        .value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.data.retryAfterSeconds")
                        .value(17));

        verify(constraintExtractionService, times(2))
                .extractAndMerge(1L, 42L, "想吃川菜");
        verify(rateLimitAuditService).recordRejected(
                eq("constraint-extract-user"),
                eq(rule),
                eq("42"),
                eq(42L),
                any(),
                any(),
                eq(new RateLimitDecision(false, 3, 2, 17))
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        String token = JwtUtil.generateToken(42L, "alice", "USER");
        return post("/api/diner/sessions/1/constraints/extract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"想吃川菜\"}");
    }

    private RateLimitProperties.Rule rule() {
        RateLimitProperties.Rule rule = new RateLimitProperties.Rule();
        rule.setEnabled(true);
        rule.setPaths(List.of(
                "/api/diner/sessions/*/constraints/extract"
        ));
        rule.setMethods(List.of("POST"));
        rule.setKeyType(RateLimitKeyType.USER);
        rule.setMaxRequests(2);
        rule.setWindowSeconds(60);
        return rule;
    }

    private static <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() { return value; }
            @Override
            public T getIfAvailable() { return value; }
            @Override
            public T getIfUnique() { return value; }
            @Override
            public T getObject(Object... args) { return value; }
        };
    }
}
