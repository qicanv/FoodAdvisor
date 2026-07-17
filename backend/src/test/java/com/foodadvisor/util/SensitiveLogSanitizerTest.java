package com.foodadvisor.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveLogSanitizerTest {

    private final SensitiveLogSanitizer sanitizer =
            new SensitiveLogSanitizer();

    @Test
    void shouldMaskSensitiveJsonFieldsAndHeaders() {
        String raw = """
                {"username":"alice","password":"plain-password","apiKey":"sk-1234567890abcdef","token":"abcdef1234567890abcdef1234567890"}
                Authorization: Bearer secret-token-value
                """;

        String sanitized = sanitizer.sanitize(raw);

        assertFalse(sanitized.contains("plain-password"));
        assertFalse(sanitized.contains("sk-1234567890abcdef"));
        assertFalse(sanitized.contains("abcdef1234567890abcdef1234567890"));
        assertFalse(sanitized.contains("secret-token-value"));
        assertTrue(sanitized.contains("alice"));
        assertTrue(sanitized.contains("****"));
    }

    @Test
    void shouldMaskSensitiveFieldByName() {
        String sanitized = sanitizer.sanitizeField(
                "Authorization",
                "Bearer full-token-value"
        );

        assertFalse(sanitized.contains("full-token-value"));
        assertTrue(sanitized.contains("****"));
    }

    @Test
    void shouldMaskKeyValueSecretSummaries() {
        String sanitized = sanitizer.sanitize(
                "audit failed: password=plain-secret token=abc123"
        );

        assertFalse(sanitized.contains("plain-secret"));
        assertFalse(sanitized.contains("abc123"));
        assertTrue(sanitized.contains("password=****"));
        assertTrue(sanitized.contains("token=****"));
    }
}
