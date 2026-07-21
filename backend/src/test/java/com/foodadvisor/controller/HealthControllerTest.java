package com.foodadvisor.controller;

import com.foodadvisor.service.AuditLogService;
import com.foodadvisor.util.SensitiveLogSanitizer;
import com.foodadvisor.config.RateLimitInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private SensitiveLogSanitizer sensitiveLogSanitizer;

    @MockitoBean
    private RateLimitInterceptor rateLimitInterceptor;

    @BeforeEach
    void allowRequestsThroughRateLimitInterceptor() {
        when(rateLimitInterceptor.preHandle(
                any(),
                any(),
                any()
        )).thenReturn(true);
    }

    @Test
    void shouldReturnBackendHealthStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Request succeeded"))
                .andExpect(jsonPath("$.data.service").value("backend"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.timestamp").exists());
    }
}
