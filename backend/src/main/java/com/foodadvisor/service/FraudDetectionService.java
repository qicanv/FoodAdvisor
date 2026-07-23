package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.config.FraudDetectionConfig;
import com.foodadvisor.dto.fraud.DetectRequest;
import com.foodadvisor.dto.fraud.DetectResponse;
import com.foodadvisor.entity.ReviewFraudCase;
import com.foodadvisor.mapper.FraudCaseQueryMapper;
import com.foodadvisor.mapper.ReviewFraudCaseMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 刷评检测引擎
 * 实现四大检测规则，由管理员手动触发
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final ReviewMapper reviewMapper;
    private final ReviewFraudCaseMapper fraudCaseMapper;
    private final FraudCaseQueryMapper queryMapper;
    private final FraudDetectionConfig config;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> RULE_NAME_MAP = Map.of(
            "CONCENTRATION", "短时间集中评价",
            "SIMILARITY", "评论内容高度相似",
            "FREQUENCY", "同一账号频繁评价",
            "RATING_ANOMALY", "评分分布异常"
    );

    /**
     * 执行刷评检测扫描（手动触发入口）
     */
    @Transactional
    public DetectResponse scan(DetectRequest request) {
        List<String> ruleTypes = request.getRuleTypes();
        if (ruleTypes == null || ruleTypes.isEmpty()) {
            ruleTypes = new ArrayList<>();
            if (config.getRules().getConcentration().isEnabled()) ruleTypes.add("CONCENTRATION");
            if (config.getRules().getSimilarity().isEnabled()) ruleTypes.add("SIMILARITY");
            if (config.getRules().getFrequency().isEnabled()) ruleTypes.add("FREQUENCY");
            if (config.getRules().getRatingAnomaly().isEnabled()) ruleTypes.add("RATING_ANOMALY");
        }

        List<DetectResponse.RuleDetectDetail> details = new ArrayList<>();
        int totalCreated = 0;

        for (String ruleType : ruleTypes) {
            List<ReviewFraudCase> cases;
            switch (ruleType.toUpperCase()) {
                case "CONCENTRATION":
                    cases = detectConcentration(request.getMerchantId());
                    break;
                case "SIMILARITY":
                    cases = detectSimilarity(request.getMerchantId());
                    break;
                case "FREQUENCY":
                    cases = detectFrequency(request.getMerchantId());
                    break;
                case "RATING_ANOMALY":
                    cases = detectRatingAnomaly(request.getMerchantId());
                    break;
                default:
                    log.warn("Unknown rule type: {}, skipping", ruleType);
                    continue;
            }

            int created = 0;
            for (ReviewFraudCase c : cases) {
                if (isNewCase(c)) {
                    fraudCaseMapper.insert(c);
                    created++;
                    updateReviewRiskLevels(c);
                }
            }

            details.add(DetectResponse.RuleDetectDetail.builder()
                    .ruleType(ruleType.toUpperCase())
                    .ruleTypeText(RULE_NAME_MAP.getOrDefault(ruleType.toUpperCase(), ruleType))
                    .casesCreated(created)
                    .build());
            totalCreated += created;
        }

        return DetectResponse.builder()
                .totalCasesCreated(totalCreated)
                .details(details)
                .build();
    }

    // ========== 规则1：集中评价检测 ==========

    private List<ReviewFraudCase> detectConcentration(Long targetMerchantId) {
        FraudDetectionConfig.ConcentrationRule rule = config.getRules().getConcentration();
        List<ReviewFraudCase> cases = new ArrayList<>();

        OffsetDateTime since = OffsetDateTime.now().minusMinutes(rule.getWindowMinutes());

        List<Map<String, Object>> results = reviewMapper.countReviewsByMerchantSince(
                since, rule.getThresholdCount(), targetMerchantId);

        for (Map<String, Object> row : results) {
            Long merchantId = ((Number) row.get("merchant_id")).longValue();
            long count = ((Number) row.get("cnt")).longValue();

            List<Long> reviewIds = reviewMapper.getReviewIdsByMerchantSince(merchantId, since);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("ruleName", RULE_NAME_MAP.get("CONCENTRATION"));
            snapshot.put("threshold", rule.getThresholdCount());
            snapshot.put("windowMinutes", rule.getWindowMinutes());
            snapshot.put("actualCount", count);

            cases.add(buildCase(merchantId, "CONCENTRATION", rule.getRiskLevel(),
                    snapshot, reviewIds,
                    String.format("商家在%d分钟内集中出现%d条评价，超过阈值%d条",
                            rule.getWindowMinutes(), count, rule.getThresholdCount())));
        }
        return cases;
    }

    // ========== 规则2：文本相似度检测 ==========

    private List<ReviewFraudCase> detectSimilarity(Long targetMerchantId) {
        FraudDetectionConfig.SimilarityRule rule = config.getRules().getSimilarity();
        List<ReviewFraudCase> cases = new ArrayList<>();

        List<Long> merchantIds = getActiveMerchantIds(targetMerchantId);
        OffsetDateTime since = OffsetDateTime.now().minusDays(7);

        for (Long merchantId : merchantIds) {
            List<Map<String, Object>> reviews = reviewMapper.getRecentReviewContents(
                    merchantId, since, 200);

            if (reviews.size() < rule.getMinGroupSize()) continue;

            List<String> texts = new ArrayList<>();
            List<Long> reviewIds = new ArrayList<>();
            for (Map<String, Object> r : reviews) {
                String content = (String) r.get("content");
                if (content != null && !content.isBlank()) {
                    texts.add(content);
                    reviewIds.add(((Number) r.get("id")).longValue());
                }
            }

            if (texts.size() < rule.getMinGroupSize()) continue;

            int n = texts.size();
            List<Set<String>> tokenSets = new ArrayList<>();
            for (String text : texts) {
                tokenSets.add(tokenize(text));
            }

            int[] parent = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double sim = jaccardSimilarity(tokenSets.get(i), tokenSets.get(j));
                    if (sim >= rule.getThreshold()) {
                        union(parent, i, j);
                    }
                }
            }

            Map<Integer, List<Integer>> clusters = new HashMap<>();
            for (int i = 0; i < n; i++) {
                int root = find(parent, i);
                clusters.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
            }

            for (Map.Entry<Integer, List<Integer>> entry : clusters.entrySet()) {
                List<Integer> indices = entry.getValue();
                if (indices.size() >= rule.getMinGroupSize()) {
                    List<Long> similarReviewIds = indices.stream()
                            .map(reviewIds::get)
                            .collect(Collectors.toList());

                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("ruleName", RULE_NAME_MAP.get("SIMILARITY"));
                    snapshot.put("threshold", rule.getThreshold());
                    snapshot.put("minGroupSize", rule.getMinGroupSize());
                    snapshot.put("actualGroupSize", indices.size());

                    cases.add(buildCase(merchantId, "SIMILARITY", rule.getRiskLevel(),
                            snapshot, similarReviewIds,
                            String.format("发现%d条内容高度相似的评价（相似度阈值%.0f%%）",
                                    indices.size(), rule.getThreshold() * 100)));
                }
            }
        }
        return cases;
    }

    // ========== 规则3：同账号频繁评价 ==========

    private List<ReviewFraudCase> detectFrequency(Long targetMerchantId) {
        FraudDetectionConfig.FrequencyRule rule = config.getRules().getFrequency();
        List<ReviewFraudCase> cases = new ArrayList<>();

        OffsetDateTime since = OffsetDateTime.now().minusHours(rule.getWindowHours());

        List<Map<String, Object>> results = reviewMapper.countReviewsByUserSince(
                since, rule.getThresholdCount());

        for (Map<String, Object> row : results) {
            Long userId = ((Number) row.get("user_id")).longValue();
            long count = ((Number) row.get("cnt")).longValue();

            List<Long> reviewIds = reviewMapper.getReviewIdsByUserSince(userId, since);

            Long merchantId = reviewIds.isEmpty() ? 0L : getReviewMerchantId(reviewIds.get(0));

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("ruleName", RULE_NAME_MAP.get("FREQUENCY"));
            snapshot.put("threshold", rule.getThresholdCount());
            snapshot.put("windowHours", rule.getWindowHours());
            snapshot.put("actualCount", count);
            snapshot.put("userId", userId);

            cases.add(buildCase(merchantId, "FREQUENCY", rule.getRiskLevel(),
                    snapshot, reviewIds,
                    String.format("用户在%d小时内发表%d条评价，超过阈值%d条",
                            rule.getWindowHours(), count, rule.getThresholdCount())));
        }
        return cases;
    }

    // ========== 规则4：评分分布异常 ==========

    private List<ReviewFraudCase> detectRatingAnomaly(Long targetMerchantId) {
        FraudDetectionConfig.RatingAnomalyRule rule = config.getRules().getRatingAnomaly();
        List<ReviewFraudCase> cases = new ArrayList<>();

        OffsetDateTime since = OffsetDateTime.now().minusMinutes(rule.getWindowMinutes());

        List<Map<String, Object>> merchantStats = reviewMapper.countReviewsByMerchantSince(
                since, rule.getMinCount(), targetMerchantId);

        for (Map<String, Object> stat : merchantStats) {
            Long merchantId = ((Number) stat.get("merchant_id")).longValue();
            long totalCount = ((Number) stat.get("cnt")).longValue();

            List<Map<String, Object>> ratingDist = reviewMapper.getRatingDistribution(
                    merchantId, since);

            int maxCount = 0;
            double maxRating = 0;
            for (Map<String, Object> dist : ratingDist) {
                long ratingCount = ((Number) dist.get("cnt")).longValue();
                if (ratingCount > maxCount) {
                    maxCount = (int) ratingCount;
                    maxRating = ((Number) dist.get("rating")).doubleValue();
                }
            }

            double ratio = (double) maxCount / totalCount;
            if (ratio >= rule.getSameRatingRatio()) {
                List<Long> reviewIds = reviewMapper.getReviewIdsByMerchantSince(merchantId, since);

                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("ruleName", RULE_NAME_MAP.get("RATING_ANOMALY"));
                snapshot.put("threshold", rule.getSameRatingRatio());
                snapshot.put("minCount", rule.getMinCount());
                snapshot.put("totalCount", totalCount);
                snapshot.put("dominantRating", maxRating);
                snapshot.put("dominantCount", maxCount);
                snapshot.put("actualRatio", Math.round(ratio * 1000.0) / 1000.0);

                cases.add(buildCase(merchantId, "RATING_ANOMALY", rule.getRiskLevel(),
                        snapshot, reviewIds,
                        String.format("商家在%d分钟内%d条评价中%.0f%%为%.0f分",
                                rule.getWindowMinutes(), totalCount, ratio * 100, maxRating)));
            }
        }
        return cases;
    }

    // ========== 辅助方法 ==========

    private ReviewFraudCase buildCase(Long merchantId, String ruleType, String riskLevel,
                                       Map<String, Object> snapshot, List<Long> reviewIds,
                                       String summary) {
        ReviewFraudCase c = new ReviewFraudCase();
        c.setMerchantId(merchantId);
        c.setRuleType(ruleType);
        c.setRiskLevel(riskLevel);
        c.setStatus("SUSPICIOUS");
        c.setDetectedAt(OffsetDateTime.now());
        c.setSummary(summary);
        try {
            c.setMatchedRuleSnapshot(objectMapper.writeValueAsString(snapshot));
            c.setMatchedReviewIds(objectMapper.writeValueAsString(reviewIds));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize snapshot/reviewIds", e);
            c.setMatchedRuleSnapshot("{}");
            c.setMatchedReviewIds("[]");
        }
        return c;
    }

    /**
     * 幂等判断：检查该案例涉及的 reviewIds 是否已被已有案例完全覆盖
     */
    private boolean isNewCase(ReviewFraudCase newCase) {
        List<Long> newIds = parseReviewIds(newCase.getMatchedReviewIds());
        if (newIds.isEmpty()) return true;

        Set<Long> newIdSet = new HashSet<>(newIds);

        // 用 FraudCaseQueryMapper（纯 MyBatis，SQL 中用 ::text 转 JSONB）
        List<Map<String, Object>> existing = queryMapper.findCaseMaps(
                null, null, null, newCase.getMerchantId(), 1000, 0);

        for (Map<String, Object> row : existing) {
            String ruleType = (String) row.get("rule_type");
            if (!newCase.getRuleType().equals(ruleType)) continue;
            List<Long> existingIds = parseReviewIds((String) row.get("matched_review_ids"));
            if (new HashSet<>(existingIds).containsAll(newIdSet)) {
                return false;
            }
        }
        return true;
    }

    private void updateReviewRiskLevels(ReviewFraudCase c) {
        List<Long> reviewIds = parseReviewIds(c.getMatchedReviewIds());
        for (Long reviewId : reviewIds) {
            reviewMapper.updateReviewRiskLevel(reviewId, c.getRiskLevel());
        }
    }

    private List<Long> parseReviewIds(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse matchedReviewIds: {}", json, e);
            return List.of();
        }
    }

    private List<Long> getActiveMerchantIds(Long targetMerchantId) {
        if (targetMerchantId != null) {
            return List.of(targetMerchantId);
        }
        return reviewMapper.getActiveMerchants().stream()
                .map(m -> ((Number) m.get("id")).longValue())
                .collect(Collectors.toList());
    }

    private Long getReviewMerchantId(Long reviewId) {
        Map<String, Object> detail = reviewMapper.getReviewDetailWithRelations(reviewId);
        if (detail != null && detail.get("merchant_id") instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    // ---- 文本相似度工具 ----

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> tokens = new HashSet<>();
        String cleaned = text.replaceAll("[\\s\\p{Punct}]+", "");
        for (int i = 0; i < cleaned.length() - 1; i++) {
            tokens.add(cleaned.substring(i, i + 2));
        }
        return tokens;
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private int find(int[] parent, int i) {
        if (parent[i] != i) {
            parent[i] = find(parent, parent[parent[i]]);
        }
        return parent[i];
    }

    private void union(int[] parent, int i, int j) {
        int ri = find(parent, i);
        int rj = find(parent, j);
        if (ri != rj) parent[ri] = rj;
    }
}
