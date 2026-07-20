package com.foodadvisor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiTraceSanitizerTest {
    private final AiTraceSanitizer sanitizer =
            new AiTraceSanitizer(new ObjectMapper(), new SensitiveLogSanitizer());

    @Test
    void redactsSecretsAndPersonalInformation() {
        String value = sanitizer.sanitizeText(
                "sk-complete-secret password=123456 Authorization: Bearer abcdef "
                        + "18146258399 test@example.com latitude=31.230416"
        );
        assertThat(value).doesNotContain("sk-complete-secret", "123456",
                "abcdef", "18146258399", "test@example.com", "31.230416");
    }

    @Test
    void jsonUsesAllowListAndReplacesCoordinates() {
        String value = sanitizer.sanitizeJson(Map.of(
                "messageId", 12,
                "content", "private message",
                "latitude", 31.230416,
                "tastePreferences", new String[]{"微辣"},
                "apiKey", "sk-complete-secret"
        ));
        assertThat(value).contains("\"messageId\":12", "\"tastePreferences\"");
        assertThat(value).doesNotContain("private message", "31.230416",
                "sk-complete-secret");
    }

    @Test
    void redactsNormalizedSecretKeysAndSensitiveValues() {
        String value = sanitizer.sanitizeJson(Map.of(
                "X-Internal-Token", "internal-secret-token",
                "x_internal_token", "internal-secret-token",
                "xInternalToken", "internal-secret-token",
                "Authorization", "Bearer abcdef",
                "access_token", "sk-complete-secret",
                "refresh-token", "sk-complete-secret",
                "private_key", "sk-complete-secret",
                "encrypted_api_key", "sk-complete-secret",
                "password", "password=123456",
                "messageId", 12
        ));

        assertThat(value).contains("\"messageId\":12");
        assertThat(value).doesNotContain("internal-secret-token", "Bearer abcdef",
                "sk-complete-secret", "password=123456");

        String text = sanitizer.sanitizeText(
                "X-Internal-Token=internal-secret-token Authorization: Bearer abcdef "
                        + "password=123456 18146258399 test@example.com latitude=31.230416"
        );
        assertThat(text).doesNotContain("internal-secret-token", "Bearer abcdef", "123456",
                "18146258399", "test@example.com", "31.230416");
    }

    @Test
    void preservesApprovedFinalOutputSummaryFields() {
        String value = sanitizer.sanitizeJson(Map.ofEntries(
                Map.entry("status", "SUCCESS"), Map.entry("degraded", false),
                Map.entry("recommendationId", 1), Map.entry("merchantIds", new Long[]{10L, 11L}),
                Map.entry("resultCount", 2), Map.entry("responseType", "RECOMMENDATION"),
                Map.entry("reviewId", 2), Map.entry("reviewAnalysisId", 3),
                Map.entry("summaryId", 4), Map.entry("merchantId", 10),
                Map.entry("replyDraftId", 5), Map.entry("reviewCount", 6),
                Map.entry("evidenceCount", 7), Map.entry("highlightIds", new Long[]{8L}),
                Map.entry("sourceReviewIds", new Long[]{9L}), Map.entry("requestedCount", 10),
                Map.entry("successCount", 8), Map.entry("failedCount", 1),
                Map.entry("skippedCount", 1), Map.entry("analysisIds", new Long[]{12L}),
                Map.entry("replyType", "POSITIVE")
        ));

        assertThat(value).contains("\"status\":\"SUCCESS\"", "\"reviewCount\":6",
                "\"highlightIds\"", "\"analysisIds\"", "\"replyType\":\"POSITIVE\"");
    }
}
