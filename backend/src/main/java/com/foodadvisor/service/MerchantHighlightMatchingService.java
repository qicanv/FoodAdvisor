package com.foodadvisor.service;

import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.RecommendationBasisVO;
import com.foodadvisor.entity.MerchantHighlight;
import com.foodadvisor.entity.MerchantHighlightEvidence;
import com.foodadvisor.entity.Review;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class MerchantHighlightMatchingService {
    public boolean isRelevant(ConstraintState state) {
        return state != null && (!empty(state.getEnvironmentRequirements())
                || !empty(state.getScenes()) || !empty(state.getTastePreferences())
                || !empty(state.getDishKeywords()));
    }

    public Map<Long, List<RecommendationBasisVO>> match(
            ConstraintState state,
            List<MerchantHighlight> highlights,
            List<MerchantHighlightEvidence> evidences,
            Map<Long, Review> reviews) {
        if (!isRelevant(state)) return Map.of();
        Map<Long, List<MerchantHighlightEvidence>> byHighlight = new HashMap<>();
        for (MerchantHighlightEvidence evidence : safe(evidences)) {
            byHighlight.computeIfAbsent(evidence.getHighlightId(), ignored -> new ArrayList<>())
                    .add(evidence);
        }
        List<String> terms = new ArrayList<>();
        terms.addAll(safe(state.getEnvironmentRequirements()));
        terms.addAll(safe(state.getScenes()));
        terms.addAll(safe(state.getTastePreferences()));
        terms.addAll(safe(state.getDishKeywords()));
        Map<Long, List<RecommendationBasisVO>> result = new LinkedHashMap<>();
        for (MerchantHighlight highlight : safe(highlights)) {
            String matched = terms.stream().filter(term -> matches(highlight, term))
                    .findFirst().orElse(null);
            if (matched == null) continue;
            List<MerchantHighlightEvidence> visibleSources = byHighlight
                    .getOrDefault(highlight.getId(), List.of()).stream()
                    .filter(e -> visibleForMerchant(reviews.get(e.getReviewId()),
                            highlight.getMerchantId())).toList();
            MerchantHighlightEvidence source = visibleSources.stream()
                    .findFirst().orElse(null);
            if (source == null) continue;
            RecommendationBasisVO basis = new RecommendationBasisVO();
            basis.setSourceType("REVIEW");
            basis.setSourceId(source.getReviewId());
            basis.setMerchantId(highlight.getMerchantId());
            basis.setTitle(highlight.getTitle());
            basis.setSummary(limit(normalizeAggregationClaim(
                    highlight.getDescription(), visibleSources.size()), 160));
            basis.setMatchedCondition(matched);
            basis.setRelevanceScore(BigDecimal.valueOf(0.8));
            result.computeIfAbsent(highlight.getMerchantId(), ignored -> new ArrayList<>());
            if (result.get(highlight.getMerchantId()).size() < 3) {
                result.get(highlight.getMerchantId()).add(basis);
            }
        }
        return result;
    }

    private boolean matches(MerchantHighlight h, String term) {
        if (term == null || term.isBlank()) return false;
        String text = ((h.getTitle() == null ? "" : h.getTitle()) + " "
                + (h.getDescription() == null ? "" : h.getDescription())).toLowerCase();
        String normalized = term.trim().toLowerCase();
        if (text.contains(normalized)) return true;
        if ("ENVIRONMENT".equals(h.getHighlightType())) {
            return containsAny(normalized, "安静", "环境", "聊天", "聚会", "约会", "包间");
        }
        if ("SERVICE".equals(h.getHighlightType())) {
            return containsAny(normalized, "服务", "上菜", "分量", "性价比");
        }
        return "SIGNATURE_DISH".equals(h.getHighlightType())
                && containsAny(normalized, "辣", "清淡", "甜", "麻辣");
    }

    private boolean visibleForMerchant(Review r, Long merchantId) {
        return r != null && Objects.equals(r.getMerchantId(), merchantId)
                && "PUBLISHED".equals(r.getStatus())
                && "APPROVED".equals(r.getModerationStatus())
                && r.getDeletedAt() == null;
    }
    private boolean containsAny(String value, String... words) {
        return Arrays.stream(words).anyMatch(value::contains);
    }
    private <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }
    private boolean empty(List<?> list) { return list == null || list.isEmpty(); }
    private String limit(String text, int max) {
        return text == null ? "" : text.substring(0, Math.min(text.length(), max));
    }
    private String normalizeAggregationClaim(String text, int visibleCount) {
        if (text == null || visibleCount >= 2) return text;
        return text.replace("多条评价", "有评价")
                .replace("多位用户", "有用户");
    }
}
