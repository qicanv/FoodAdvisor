package com.foodadvisor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
public class BehaviorAnalysisService {

    private final Set<String> reportedEventIds = new HashSet<>();

    public Map<String, Object> getHotSearchKeywords(OffsetDateTime startTime, OffsetDateTime endTime, Integer limit) {
        List<Map<String, Object>> keywords = Arrays.asList(
                Map.of("keyword", "川菜", "count", 1234, "trend", 15.5),
                Map.of("keyword", "火锅", "count", 987, "trend", 8.3),
                Map.of("keyword", "日料", "count", 856, "trend", -2.1),
                Map.of("keyword", "烧烤", "count", 723, "trend", 22.8),
                Map.of("keyword", "粤菜", "count", 654, "trend", 5.6),
                Map.of("keyword", "西餐", "count", 543, "trend", -3.2),
                Map.of("keyword", "海鲜", "count", 432, "trend", 18.9),
                Map.of("keyword", "甜品", "count", 389, "trend", 12.4),
                Map.of("keyword", "自助", "count", 345, "trend", -1.5),
                Map.of("keyword", "快餐", "count", 298, "trend", 6.7)
        );

        Map<String, Object> result = new HashMap<>();
        result.put("keywords", keywords);
        result.put("totalCount", keywords.stream().mapToInt(k -> (Integer) k.get("count")).sum());
        result.put("timeRange", Map.of("startTime", startTime, "endTime", endTime));

        return result;
    }

    public Map<String, Object> getHotScenarios(OffsetDateTime startTime, OffsetDateTime endTime, Integer limit) {
        List<Map<String, Object>> scenarios = Arrays.asList(
                Map.of("scenario", "朋友聚餐", "count", 2345, "percentage", 28.5),
                Map.of("scenario", "情侣约会", "count", 1876, "percentage", 22.8),
                Map.of("scenario", "家庭聚餐", "count", 1567, "percentage", 19.1),
                Map.of("scenario", "商务宴请", "count", 987, "percentage", 12.0),
                Map.of("scenario", "单人就餐", "count", 765, "percentage", 9.3),
                Map.of("scenario", "生日庆祝", "count", 543, "percentage", 6.6),
                Map.of("scenario", "团建聚会", "count", 134, "percentage", 1.6)
        );

        Map<String, Object> result = new HashMap<>();
        result.put("scenarios", scenarios);
        result.put("totalCount", scenarios.stream().mapToInt(s -> (Integer) s.get("count")).sum());
        result.put("timeRange", Map.of("startTime", startTime, "endTime", endTime));

        return result;
    }

    public Map<String, Object> getHotMerchants(OffsetDateTime startTime, OffsetDateTime endTime, Integer limit) {
        List<Map<String, Object>> merchants = Arrays.asList(
                Map.of("merchantId", 1L, "merchantName", "川味小厨", "category", "川菜", "clickCount", 892, "viewCount", 2341, "conversionRate", 15.6),
                Map.of("merchantId", 2L, "merchantName", "海底捞火锅", "category", "火锅", "clickCount", 756, "viewCount", 1890, "conversionRate", 12.3),
                Map.of("merchantId", 3L, "merchantName", "寿司之神", "category", "日料", "clickCount", 634, "viewCount", 1567, "conversionRate", 18.9),
                Map.of("merchantId", 4L, "merchantName", "老北京烧烤", "category", "烧烤", "clickCount", 523, "viewCount", 1345, "conversionRate", 14.2),
                Map.of("merchantId", 5L, "merchantName", "粤港茶餐厅", "category", "粤菜", "clickCount", 456, "viewCount", 1123, "conversionRate", 16.7),
                Map.of("merchantId", 6L, "merchantName", "法式西餐厅", "category", "西餐", "clickCount", 389, "viewCount", 987, "conversionRate", 19.2),
                Map.of("merchantId", 7L, "merchantName", "海鲜盛宴", "category", "海鲜", "clickCount", 345, "viewCount", 876, "conversionRate", 13.5),
                Map.of("merchantId", 8L, "merchantName", "甜蜜时光", "category", "甜品", "clickCount", 298, "viewCount", 765, "conversionRate", 17.8)
        );

        Map<String, Object> result = new HashMap<>();
        result.put("merchants", merchants);
        result.put("totalClickCount", merchants.stream().mapToInt(m -> (Integer) m.get("clickCount")).sum());
        result.put("timeRange", Map.of("startTime", startTime, "endTime", endTime));

        return result;
    }

    public Map<String, Object> getRecommendationStats(OffsetDateTime startTime, OffsetDateTime endTime) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecommendations", 15678);
        stats.put("totalClicks", 3456);
        stats.put("totalFeedbacks", 876);
        stats.put("positiveFeedbacks", 654);
        stats.put("negativeFeedbacks", 222);
        stats.put("clickConversionRate", 22.1);
        stats.put("feedbackRate", 5.6);
        stats.put("positiveRate", 74.7);

        List<Map<String, Object>> dailyTrend = Arrays.asList(
                Map.of("date", "2026-07-15", "recommendations", 456, "clicks", 102, "feedbacks", 28),
                Map.of("date", "2026-07-16", "recommendations", 512, "clicks", 118, "feedbacks", 32),
                Map.of("date", "2026-07-17", "recommendations", 489, "clicks", 98, "feedbacks", 25),
                Map.of("date", "2026-07-18", "recommendations", 567, "clicks", 134, "feedbacks", 38),
                Map.of("date", "2026-07-19", "recommendations", 623, "clicks", 145, "feedbacks", 42),
                Map.of("date", "2026-07-20", "recommendations", 598, "clicks", 128, "feedbacks", 35),
                Map.of("date", "2026-07-21", "recommendations", 543, "clicks", 119, "feedbacks", 30)
        );

        stats.put("dailyTrend", dailyTrend);
        stats.put("timeRange", Map.of("startTime", startTime, "endTime", endTime));

        return stats;
    }

    public Map<String, Object> getBehaviorOverview(OffsetDateTime startTime, OffsetDateTime endTime) {
        Map<String, Object> overview = new HashMap<>();
        overview.put("totalSearches", 8976);
        overview.put("totalClicks", 4567);
        overview.put("totalFeedbacks", 1234);
        overview.put("activeUsers", 2345);
        overview.put("avgSearchesPerUser", 3.8);
        overview.put("avgClicksPerSearch", 0.51);

        Map<String, Object> searchByCuisine = new HashMap<>();
        searchByCuisine.put("川菜", 1890);
        searchByCuisine.put("火锅", 1456);
        searchByCuisine.put("日料", 1234);
        searchByCuisine.put("烧烤", 987);
        searchByCuisine.put("粤菜", 876);
        searchByCuisine.put("西餐", 765);
        searchByCuisine.put("其他", 1778);
        overview.put("searchByCuisine", searchByCuisine);

        Map<String, Object> searchByPriceRange = new HashMap<>();
        searchByPriceRange.put("0-50", 2345);
        searchByPriceRange.put("50-100", 3456);
        searchByPriceRange.put("100-200", 1890);
        searchByPriceRange.put("200-500", 987);
        searchByPriceRange.put("500+", 298);
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
}