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
        Map<Long, List<RecommendationBasisVO>> result = new LinkedHashMap<>();
        for (MerchantHighlight highlight : safe(highlights)) {
            List<MerchantHighlightEvidence> visibleSources = byHighlight
                    .getOrDefault(highlight.getId(), List.of()).stream()
                    .filter(e -> visibleForMerchant(reviews.get(e.getReviewId()),
                            highlight.getMerchantId())).toList();
            Match match = findMatch(state, highlight, visibleSources, reviews);
            if (match == null) continue;
            MerchantHighlightEvidence source = "TASTE".equals(highlight.getHighlightType())
                    ? visibleSources.stream()
                    .filter(e -> matchesTasteText(
                            reviews.get(e.getReviewId()).getContent(), match.term()))
                    .findFirst().orElse(null)
                    : visibleSources.stream().findFirst().orElse(null);
            if (source == null) continue;
            RecommendationBasisVO basis = new RecommendationBasisVO();
            basis.setSourceType("REVIEW");
            basis.setSourceId(source.getReviewId());
            basis.setMerchantId(highlight.getMerchantId());
            basis.setTitle(highlight.getTitle());
            basis.setSummary(limit(normalizeAggregationClaim(
                    highlight.getDescription(), visibleSources.size()), 160));
            basis.setMatchedCondition(match.conditionKey());
            basis.setRelevanceScore(BigDecimal.valueOf(0.8));
            result.computeIfAbsent(highlight.getMerchantId(), ignored -> new ArrayList<>());
            if (result.get(highlight.getMerchantId()).size() < 3) {
                result.get(highlight.getMerchantId()).add(basis);
            }
        }
        return result;
    }

    private Match findMatch(
            ConstraintState state,
            MerchantHighlight highlight,
            List<MerchantHighlightEvidence> visibleSources,
            Map<Long, Review> reviews
    ) {
        if ("TASTE".equals(highlight.getHighlightType())) {
            for (String term : safe(state.getTastePreferences())) {
                boolean highlightMatches = matchesText(highlight, term);
                boolean reviewMatches = visibleSources.stream()
                        .map(e -> reviews.get(e.getReviewId()))
                        .anyMatch(review -> matchesTasteText(review.getContent(), term));
                if ((highlightMatches || reviewMatches) && reviewMatches) {
                    return new Match("tastePreferences", term);
                }
            }
            return null;
        }
        List<Match> candidates = new ArrayList<>();
        safe(state.getEnvironmentRequirements()).forEach(term ->
                candidates.add(new Match("environmentRequirements", term)));
        safe(state.getScenes()).forEach(term ->
                candidates.add(new Match("scenes", term)));
        safe(state.getDishKeywords()).forEach(term ->
                candidates.add(new Match("dishKeywords", term)));
        return candidates.stream()
                .filter(candidate -> matches(highlight, candidate.term()))
                .findFirst().orElse(null);
    }

    private boolean matches(MerchantHighlight h, String term) {
        if (term == null || term.isBlank()) return false;
        if (matchesText(h, term)) return true;
        String normalized = term.trim().toLowerCase();
        if ("ENVIRONMENT".equals(h.getHighlightType())) {
            return containsAny(normalized, "安静", "环境", "聊天", "聚会", "约会", "包间");
        }
        if ("SERVICE".equals(h.getHighlightType())) {
            return containsAny(normalized, "服务", "上菜", "分量", "性价比");
        }
        return "SIGNATURE_DISH".equals(h.getHighlightType())
                && containsAny(normalized, "辣", "清淡", "甜", "麻辣");
    }

    private boolean matchesText(MerchantHighlight highlight, String term) {
        if (term == null || term.isBlank()) return false;
        String text = ((highlight.getTitle() == null ? "" : highlight.getTitle()) + " "
                + (highlight.getDescription() == null ? "" : highlight.getDescription()))
                .toLowerCase();
        return text.contains(term.trim().toLowerCase());
    }

    private boolean matchesTasteText(String text, String term) {
        if (text == null || term == null || term.isBlank()) return false;
        String normalizedText = text.toLowerCase();
        String normalizedTerm = term.trim().toLowerCase();
        if (normalizedText.contains(normalizedTerm)) return true;
        if (containsAny(normalizedTerm, "微辣", "小辣", "轻辣")) {
            return containsAny(normalizedText, "微辣", "小辣", "轻辣");
        }
        return false;
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
    private record Match(String conditionKey, String term) {
    }

    private String normalizeAggregationClaim(String text, int visibleCount) {
        if (text == null || visibleCount >= 2) return text;
        return text.replace("多条评价", "有评价")
                .replace("多位用户", "有用户");
    }
}
