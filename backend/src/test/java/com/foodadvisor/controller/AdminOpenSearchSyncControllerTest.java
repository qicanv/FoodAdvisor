package com.foodadvisor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.AIClientService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AdminOpenSearchSyncControllerTest {

    @Test
    void comparesValidPostgresSourcesWithDistinctActiveIndexSources()
            throws Exception {
        AdminAccessGuard guard = mock(AdminAccessGuard.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AIClientService aiClient = mock(AIClientService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(2L, 3L, 4L);
        when(aiClient.getActiveKnowledgeCounts()).thenReturn(
                new ObjectMapper().readTree("""
                        {
                          "activeDocumentCount": 12,
                          "activeDistinctSourceCounts": {
                            "MERCHANT_INTRO": 2,
                            "MENU": 2,
                            "REVIEW": 4
                          }
                        }
                        """)
        );

        ApiResponse<Map<String, Object>> response =
                new AdminOpenSearchSyncController(guard, jdbc, aiClient)
                        .reconciliation(request);

        assertEquals(true, response.data().get("openSearchAvailable"));
        assertEquals(
                Map.of("MERCHANT_INTRO", 0L, "MENU", 1L, "REVIEW", 0L),
                response.data().get("postgresMinusOpenSearch")
        );
        verify(guard).requireAdmin(request);
    }

    @Test
    void keepsPostgresCountsAvailableWhenOpenSearchIsDown() {
        AdminAccessGuard guard = mock(AdminAccessGuard.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AIClientService aiClient = mock(AIClientService.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(1L, 1L, 1L);
        when(aiClient.getActiveKnowledgeCounts())
                .thenThrow(new RuntimeException("unavailable"));

        ApiResponse<Map<String, Object>> response =
                new AdminOpenSearchSyncController(guard, jdbc, aiClient)
                        .reconciliation(mock(HttpServletRequest.class));

        assertEquals(false, response.data().get("openSearchAvailable"));
        assertEquals(
                Map.of("MERCHANT_INTRO", 1L, "MENU", 1L, "REVIEW", 1L),
                response.data().get("postgresValidSourceCounts")
        );
    }
}
