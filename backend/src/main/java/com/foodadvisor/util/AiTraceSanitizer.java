package com.foodadvisor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AiTraceSanitizer {
    private static final String REDACTED = "***REDACTED***";
    private static final int MAX_TEXT = 500;
    private static final Set<String> SECRET_NAMES = Set.of(
            "password", "confirmpassword", "apikey", "secret", "token",
            "accesstoken", "refreshtoken", "authorization", "cookie",
            "privatekey", "encryptedapikey", "xinternaltoken",
            "databasepassword", "dbpassword"
    );
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "messageid", "messagelength", "intent", "confidence", "extractor",
            "degraded", "responsetype", "recommendationid", "summaryid",
            "reviewanalysisid", "replydraftid", "resultcount", "merchantids",
            "partysize", "budgetmin", "budgetmax", "cuisinepreferences",
            "tastepreferences", "distancekm", "diningpurpose", "haslocation",
            "sourcetype", "sourceid", "documentid", "chunkid", "merchantid",
            "merchantname", "rankno", "relevancescore", "retrievalmode",
            "modelname", "modelversion", "promptversion", "provider",
            "status", "reviewid", "reviewcount", "evidencecount", "highlightids",
            "sourcereviewids", "requestedcount", "successcount", "failedcount",
            "skippedcount", "analysisids", "replytype", "sentiment"
    );
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1\\d{2})\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?i)([A-Z0-9._%+-])[^@\\s]*(@[A-Z0-9.-]+\\.[A-Z]{2,})");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{6})\\d{8,11}([\\dXx])(?!\\d)");
    private static final Pattern COORDINATE = Pattern.compile("(?i)(latitude|longitude|lat|lng)\\s*[=:]\\s*-?\\d+(?:\\.\\d+)?");

    private final ObjectMapper objectMapper;
    private final SensitiveLogSanitizer base;

    public AiTraceSanitizer(ObjectMapper objectMapper, SensitiveLogSanitizer base) {
        this.objectMapper = objectMapper;
        this.base = base;
    }

    public String sanitizeText(String value) {
        if (value == null) return null;
        String result = base.sanitize(value);
        result = PHONE.matcher(result).replaceAll("$1****$2");
        result = EMAIL.matcher(result).replaceAll("$1***$2");
        result = ID_CARD.matcher(result).replaceAll("$1********$2");
        result = COORDINATE.matcher(result).replaceAll("$1=***REDACTED***");
        return result.length() > MAX_TEXT ? result.substring(0, MAX_TEXT) : result;
    }

    public String sanitizeJson(Object value) {
        try {
            JsonNode source = value instanceof String text
                    ? objectMapper.readTree(text)
                    : objectMapper.valueToTree(value);
            JsonNode clean = clean(source);
            return clean == null ? "{}" : objectMapper.writeValueAsString(clean);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private JsonNode clean(JsonNode node) {
        if (node == null || node.isNull()) return node;
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            int count = 0;
            for (JsonNode child : node) {
                if (count++ >= 50) break;
                result.add(clean(child));
            }
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                String normalized = normalizeKey(entry.getKey());
                if (SECRET_NAMES.contains(normalized)) {
                    result.put(entry.getKey(), REDACTED);
                } else if (ALLOWED_FIELDS.contains(entry.getKey())
                        || ALLOWED_FIELDS.contains(normalized)) {
                    if (normalized.equals("latitude") || normalized.equals("longitude")
                            || normalized.equals("lat") || normalized.equals("lng")) {
                        result.put("hasLocation", true);
                    } else {
                        result.set(entry.getKey(), clean(entry.getValue()));
                    }
                }
            });
            return result;
        }
        if (node.isTextual()) return objectMapper.getNodeFactory().textNode(sanitizeText(node.asText()));
        return node;
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "")
                .toLowerCase(Locale.ROOT);
    }
}
