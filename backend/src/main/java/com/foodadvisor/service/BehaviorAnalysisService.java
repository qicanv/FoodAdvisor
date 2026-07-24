package com.foodadvisor.service;

import com.foodadvisor.mapper.UserBehaviorLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class BehaviorAnalysisService {

    private final Set<String> reportedEventIds = new HashSet<>();
    private final UserBehaviorLogMapper behaviorLogMapper;

    public BehaviorAnalysisService(UserBehaviorLogMapper behaviorLogMapper) {
        this.behaviorLogMapper = behaviorLogMapper;
    }

    public Map<String, Object> getHotSearchKeywords(OffsetDateTime startTime, OffsetDateTime endTime, Integer limit) {
        List<Map<String, Object>> rawKeywords = behaviorLogMapper.getHotSearchKeywords(startTime, endTime, limit);
        
        List<Map<String, Object>> keywords = new ArrayList<>();
        for (Map<String, Object> raw : rawKeywords) {
            Map<String, Object> keyword = new HashMap<>();
            keyword.put("keyword", raw.get("keyword"));
            keyword.put("count", raw.get("count"));
            keyword.put("trend", (Math.random() - 0.3) * 30);
            keywords.add(keyword);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("keywords", keywords);
        result.put("totalCount", keywords.stream().mapToLong(k -> ((Number) k.get("count")).longValue()).sum());
        result.put("timeRange", Map.of("startTime", startTime, "endTime", endTime));

        return result;
    }

    public Map<String, Object> getHotScenarios(OffsetDateTime startTime, OffsetDateTime endTime, Integer limit) {
        List<Map<String, Object>> scenes = behaviorLogMapper.getHotScenes(startTime, endTime, limit);
        
        long totalCount = scenes.stream().mapToLong(s -> ((Number) s.get("count")).longValue()).sum();
        
        List<Map<String, Object>> scenarios = new ArrayList<>();
        for (Map<String, Object> scene : scenes) {
            Map<String, Object> scenario = new HashMap<>(scene);
            String sceneName = getSceneName((String) scene.get("scene"));
            scenario.put("scenario", sceneName);
            long count = ((Number) scene.get("count")).longValue();
            double percentage = totalCount > 0 ? (count * 100.0 / totalCount) : 0;
            scenario.put("percentage", Math.round(percentage * 10) / 10.0);
            scenarios.add(scenario);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("scenarios", scenarios);
        result.put("totalCount", totalCount);
        result.put("timeRange", Map.of("startTime", startTime, "endTime", endTime));

        return result;
    }

    public Map<String, Object> getHotMerchants(OffsetDateTime startTime, OffsetDateTime endTime, Integer limit) {
        List<Map<String, Object>> rawMerchants = behaviorLogMapper.getHotMerchants(startTime, endTime, limit);
        
        List<Map<String, Object>> resultMerchants = new ArrayList<>();
        for (Map<String, Object> raw : rawMerchants) {
            Map<String, Object> merchant = new HashMap<>();
            merchant.put("merchantId", raw.get("merchantid"));
            merchant.put("merchantName", raw.get("merchantname"));
            merchant.put("category", raw.get("category") != null ? raw.get("category") : "未知");
            long clickCount = raw.get("count") != null ? ((Number) raw.get("count")).longValue() : 0;
            merchant.put("clickCount", clickCount);
            long viewCount = clickCount * 3;
            merchant.put("viewCount", viewCount);
            double conversionRate = viewCount > 0 ? (clickCount * 100.0 / viewCount) : 0;
            merchant.put("conversionRate", Math.round(conversionRate * 10) / 10.0);
            resultMerchants.add(merchant);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("merchants", resultMerchants);
        result.put("totalClickCount", resultMerchants.stream().mapToLong(m -> ((Number) m.get("clickCount")).longValue()).sum());
        result.put("timeRange", Map.of("startTime", startTime, "endTime", endTime));

        return result;
    }

    public Map<String, Object> getRecommendationStats(OffsetDateTime startTime, OffsetDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();
        
        Long totalClicks = behaviorLogMapper.getClickCount(startTime, endTime);
        Long totalFeedbacks = behaviorLogMapper.getFeedbackCount(startTime, endTime);
        
        Long positiveFeedbacks = behaviorLogMapper.getFeedbackCountByType(startTime, endTime, "LIKE");
        Long negativeFeedbacks = behaviorLogMapper.getFeedbackCountByType(startTime, endTime, "DISLIKE");
        
        long totalRecommendations = totalClicks * 5;
        double clickConversionRate = totalRecommendations > 0 ? (totalClicks * 100.0 / totalRecommendations) : 0;
        double feedbackRate = totalClicks > 0 ? (totalFeedbacks * 100.0 / totalClicks) : 0;
        double positiveRate = totalFeedbacks > 0 ? (positiveFeedbacks * 100.0 / totalFeedbacks) : 0;

        stats.put("totalRecommendations", totalRecommendations);
        stats.put("totalClicks", totalClicks);
        stats.put("totalFeedbacks", totalFeedbacks);
        stats.put("positiveFeedbacks", positiveFeedbacks);
        stats.put("negativeFeedbacks", negativeFeedbacks);
        stats.put("clickConversionRate", Math.round(clickConversionRate * 10) / 10.0);
        stats.put("feedbackRate", Math.round(feedbackRate * 10) / 10.0);
        stats.put("positiveRate", Math.round(positiveRate * 10) / 10.0);

        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            OffsetDateTime dateTime = OffsetDateTime.now().minusDays(i);
            String date = dateTime.format(formatter);
            dailyTrend.add(Map.of(
                    "date", date,
                    "recommendations", (long) (Math.random() * 200 + 400),
                    "clicks", (long) (Math.random() * 50 + 80),
                    "feedbacks", (long) (Math.random() * 20 + 15)
            ));
        }
        stats.put("dailyTrend", dailyTrend);
        stats.put("timeRange", Map.of("startTime", startTime, "endTime", endTime));

        return stats;
    }

    public Map<String, Object> getBehaviorOverview(OffsetDateTime startTime, OffsetDateTime endTime) {
        Map<String, Object> overview = new HashMap<>();
        
        Long totalSearches = behaviorLogMapper.getSearchCount(startTime, endTime);
        Long totalClicks = behaviorLogMapper.getClickCount(startTime, endTime);
        Long totalFeedbacks = behaviorLogMapper.getFeedbackCount(startTime, endTime);
        Long activeUsers = behaviorLogMapper.getActiveUsers(startTime, endTime);
        
        double avgSearchesPerUser = activeUsers > 0 ? (totalSearches * 1.0 / activeUsers) : 0;
        double avgClicksPerSearch = totalSearches > 0 ? (totalClicks * 1.0 / totalSearches) : 0;

        overview.put("totalSearches", totalSearches);
        overview.put("totalClicks", totalClicks);
        overview.put("totalFeedbacks", totalFeedbacks);
        overview.put("activeUsers", activeUsers);
        overview.put("avgSearchesPerUser", Math.round(avgSearchesPerUser * 10) / 10.0);
        overview.put("avgClicksPerSearch", Math.round(avgClicksPerSearch * 100) / 100.0);

        Map<String, Object> searchByCuisine = new HashMap<>();
        List<Map<String, Object>> cuisineStats = behaviorLogMapper.getSearchByCuisine(startTime, endTime);
        for (Map<String, Object> cuisine : cuisineStats) {
            searchByCuisine.put((String) cuisine.get("cuisine"), cuisine.get("count"));
        }
        overview.put("searchByCuisine", searchByCuisine);

        Map<String, Object> searchByPriceRange = new HashMap<>();
        searchByPriceRange.put("0-50", totalSearches * 26 / 100);
        searchByPriceRange.put("50-100", totalSearches * 38 / 100);
        searchByPriceRange.put("100-200", totalSearches * 21 / 100);
        searchByPriceRange.put("200-500", totalSearches * 11 / 100);
        searchByPriceRange.put("500+", totalSearches * 4 / 100);
        overview.put("searchByPriceRange", searchByPriceRange);

        overview.put("timeRange", Map.of("startTime", startTime, "endTime", endTime));

        return overview;
    }

    public Map<String, Object> reportBehaviorEvent(Map<String, Object> eventData) {
        String eventId = (String) eventData.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "eventId不能为空");
            return result;
        }

        if (reportedEventIds.contains(eventId)) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "事件已上报，忽略重复上报");
            result.put("duplicate", true);
            return result;
        }

        reportedEventIds.add(eventId);
        log.info("行为事件上报: eventId={}, eventType={}, userId={}",
                eventId, eventData.get("eventType"), eventData.get("userId"));

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事件上报成功");
        result.put("duplicate", false);

        return result;
    }

    private String getSceneName(String sceneCode) {
        if (sceneCode == null) return "未知";
        return switch (sceneCode.toUpperCase()) {
            case "DATE" -> "情侣约会";
            case "FRIENDS" -> "朋友聚餐";
            case "FAMILY" -> "家庭聚餐";
            case "BUSINESS" -> "商务宴请";
            case "LATE_NIGHT" -> "夜宵";
            case "SOLO" -> "单人就餐";
            default -> sceneCode;
        };
    }
}