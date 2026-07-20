package com.foodadvisor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.math.BigDecimal;
import java.util.List;

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

    @Test
    void preservesAllDiningConstraintFields() throws Exception {
        ConstraintState conditions = new ConstraintState();
        conditions.setPartySize(2);
        conditions.setTotalBudget(new BigDecimal("200"));
        conditions.setPerCapitaBudget(new BigDecimal("100"));
        conditions.setMerchantTypes(List.of("中餐"));
        conditions.setCuisines(List.of("川菜"));
        conditions.setTastePreferences(List.of("微辣"));
        conditions.setTasteRestrictions(List.of("不吃花生"));
        conditions.setDishKeywords(List.of("水煮鱼"));
        conditions.setExcludedCuisines(List.of("粤菜"));
        conditions.setExcludedMerchantTypes(List.of("烧烤"));
        conditions.setDistanceKm(new BigDecimal("3"));
        conditions.setMinRating(new BigDecimal("4.5"));
        conditions.setScenes(List.of("朋友聚会"));
        conditions.setEnvironmentRequirements(List.of("安静"));
        conditions.setBusinessTime("TONIGHT");
        conditions.setBusinessTargetTime("20:00");
        conditions.setBusinessTargetNextDay(false);

        String value = sanitizer.sanitizeJson(conditions);
        JsonNode json = new ObjectMapper().readTree(value);

        assertThat(json.path("partySize").asInt()).isEqualTo(2);
        assertThat(json.path("totalBudget").decimalValue())
                .isEqualByComparingTo(new BigDecimal("200"));
        assertThat(json.path("perCapitaBudget").decimalValue())
                .isEqualByComparingTo(new BigDecimal("100"));
        assertThat(json.path("merchantTypes").get(0).asText()).isEqualTo("中餐");
        assertThat(json.path("cuisines").get(0).asText()).isEqualTo("川菜");
        assertThat(json.path("tastePreferences").get(0).asText()).isEqualTo("微辣");
        assertThat(json.path("tasteRestrictions").get(0).asText()).isEqualTo("不吃花生");
        assertThat(json.path("dishKeywords").get(0).asText()).isEqualTo("水煮鱼");
        assertThat(json.path("excludedCuisines").get(0).asText()).isEqualTo("粤菜");
        assertThat(json.path("excludedMerchantTypes").get(0).asText()).isEqualTo("烧烤");
        assertThat(json.path("distanceKm").decimalValue())
                .isEqualByComparingTo(new BigDecimal("3"));
        assertThat(json.path("minRating").decimalValue())
                .isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(json.path("scenes").get(0).asText()).isEqualTo("朋友聚会");
        assertThat(json.path("environmentRequirements").get(0).asText())
                .isEqualTo("安静");
        assertThat(json.path("businessTime").asText()).isEqualTo("TONIGHT");
        assertThat(json.path("businessTargetTime").asText()).isEqualTo("20:00");
        assertThat(json.path("businessTargetNextDay").asBoolean()).isFalse();
    }
}
