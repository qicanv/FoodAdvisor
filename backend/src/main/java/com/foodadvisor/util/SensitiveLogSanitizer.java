package com.foodadvisor.util;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class SensitiveLogSanitizer {

    private static final String MASK = "****";

    private static final Pattern JSON_SECRET_FIELD = Pattern.compile(
            "(?i)(\"(?:password|confirmPassword|apiKey|token|authorization|x-internal-token|secret|encryptedApiKey)\"\\s*:\\s*\")([^\"]*)(\")"
    );

    private static final Pattern HEADER_SECRET = Pattern.compile(
            "(?i)((?:Authorization|X-Internal-Token)\\s*[:=]\\s*)(Bearer\\s+)?([^\\s,;]+)"
    );

    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)((?:password|confirmPassword|apiKey|api_key|token|secret|authorization|encryptedApiKey)\\s*[=:]\\s*)([^\\s,;]+)"
    );

    private static final Pattern LONG_SECRET = Pattern.compile(
            "(?i)(sk-[A-Za-z0-9_-]{8,}|[A-Za-z0-9_=-]{32,})"
    );

    public String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String sanitized = JSON_SECRET_FIELD
                .matcher(value)
                .replaceAll("$1" + MASK + "$3");
        sanitized = HEADER_SECRET
                .matcher(sanitized)
                .replaceAll("$1$2" + MASK);
        sanitized = KEY_VALUE_SECRET
                .matcher(sanitized)
                .replaceAll("$1" + MASK);
        return LONG_SECRET
                .matcher(sanitized)
                .replaceAll(match -> maskLongSecret(match.group()));
    }

    public String sanitizeField(String fieldName, String value) {
        if (value == null) {
            return null;
        }

        if (isSensitiveField(fieldName)) {
            return MASK;
        }

        return sanitize(value);
    }

    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }

        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("authorization")
                || normalized.contains("apikey")
                || normalized.contains("api_key")
                || normalized.contains("secret")
                || normalized.contains("encryptedapikey");
    }

    private String maskLongSecret(String value) {
        if (value.length() <= 8) {
            return MASK;
        }

        return value.substring(0, 4)
                + MASK
                + value.substring(value.length() - 4);
    }
}
