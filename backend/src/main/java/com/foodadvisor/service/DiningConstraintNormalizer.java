package com.foodadvisor.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared vocabulary normalization for model patches, rule fallback,
 * stored state, matching and semantic query construction.
 */
public final class DiningConstraintNormalizer {

    private static final Map<String, String> SYNONYMS =
            new LinkedHashMap<>();

    static {
        SYNONYMS.put("朋友聚会", "适合聚餐");
        SYNONYMS.put("朋友聚餐", "适合聚餐");
        SYNONYMS.put("聚个餐", "适合聚餐");
        SYNONYMS.put("聚餐", "适合聚餐");
        SYNONYMS.put("拍照好看", "适合拍照");
        SYNONYMS.put("好拍照", "适合拍照");
        SYNONYMS.put("有氛围", "氛围感");
        SYNONYMS.put("有氛围感", "氛围感");
        SYNONYMS.put("宵夜", "夜宵");
        SYNONYMS.put("不吵", "安静");
        SYNONYMS.put("清静", "安静");
        SYNONYMS.put("咖啡", "咖啡甜品");
    }

    private DiningConstraintNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim()
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        return SYNONYMS.getOrDefault(normalized, normalized);
    }

    public static List<String> normalizeList(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalize(value);
                if (normalized != null && normalized.length() <= 30) {
                    result.add(normalized);
                }
            }
        }
        return new ArrayList<>(result);
    }
}
