package com.foodadvisor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.AIClientService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only PostgreSQL/OpenSearch reconciliation view.
 */
@RestController
@RequestMapping("/api/admin/opensearch-sync")
public class AdminOpenSearchSyncController {

    private final AdminAccessGuard adminAccessGuard;
    private final JdbcTemplate jdbcTemplate;
    private final AIClientService aiClientService;

    public AdminOpenSearchSyncController(
            AdminAccessGuard adminAccessGuard,
            JdbcTemplate jdbcTemplate,
            AIClientService aiClientService
    ) {
        this.adminAccessGuard = adminAccessGuard;
        this.jdbcTemplate = jdbcTemplate;
        this.aiClientService = aiClientService;
    }

    @GetMapping("/reconciliation")
    public ApiResponse<Map<String, Object>> reconciliation(
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Map<String, Long> postgres = new LinkedHashMap<>();
        postgres.put("MERCHANT_INTRO", count("""
                SELECT COUNT(*)
                FROM merchants
                WHERE platform_status = 'ACTIVE'
                  AND operation_status = 'OPERATING'
                  AND deleted_at IS NULL
                """));
        postgres.put("MENU", count("""
                SELECT COUNT(*)
                FROM dishes d
                JOIN merchants m ON m.id = d.merchant_id
                WHERE d.status = 'ACTIVE'
                  AND m.platform_status = 'ACTIVE'
                  AND m.operation_status = 'OPERATING'
                  AND m.deleted_at IS NULL
                """));
        postgres.put("REVIEW", count("""
                SELECT COUNT(*)
                FROM reviews r
                JOIN merchants m ON m.id = r.merchant_id
                WHERE r.status = 'PUBLISHED'
                  AND r.moderation_status = 'APPROVED'
                  AND r.deleted_at IS NULL
                  AND m.platform_status = 'ACTIVE'
                  AND m.operation_status = 'OPERATING'
                  AND m.deleted_at IS NULL
                """));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("postgresValidSourceCounts", postgres);
        try {
            JsonNode index = aiClientService.getActiveKnowledgeCounts();
            JsonNode counts = index.path("activeDistinctSourceCounts");
            Map<String, Long> openSearch = new LinkedHashMap<>();
            Map<String, Long> difference = new LinkedHashMap<>();
            for (String sourceType : postgres.keySet()) {
                long indexed = counts.path(sourceType).asLong(0L);
                openSearch.put(sourceType, indexed);
                difference.put(sourceType, postgres.get(sourceType) - indexed);
            }
            body.put("openSearchAvailable", true);
            body.put(
                    "openSearchActiveDocumentCount",
                    index.path("activeDocumentCount").asLong(0L)
            );
            body.put("openSearchActiveDistinctSourceCounts", openSearch);
            body.put("postgresMinusOpenSearch", difference);
        } catch (RuntimeException exception) {
            body.put("openSearchAvailable", false);
            body.put("openSearchActiveDistinctSourceCounts", Map.of());
            body.put("postgresMinusOpenSearch", Map.of());
        }
        return ApiResponse.success(body);
    }

    private long count(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0L : result;
    }
}
