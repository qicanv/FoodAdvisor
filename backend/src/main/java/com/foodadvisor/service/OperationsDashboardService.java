package com.foodadvisor.service;

import com.foodadvisor.mapper.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OperationsDashboardService {

    private final UserMapper userMapper;
    private final ReviewMapper reviewMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final RecommendationMapper recommendationMapper;
    private final RecommendationFeedbackMapper recommendationFeedbackMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final AuditLogMapper auditLogMapper;
    private final MerchantMapper merchantMapper;

    public OperationsDashboardService(
            UserMapper userMapper,
            ReviewMapper reviewMapper,
            AiCallLogMapper aiCallLogMapper,
            RecommendationMapper recommendationMapper,
            RecommendationFeedbackMapper recommendationFeedbackMapper,
            ChatSessionMapper chatSessionMapper,
            AuditLogMapper auditLogMapper,
            MerchantMapper merchantMapper
    ) {
        this.userMapper = userMapper;
        this.reviewMapper = reviewMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.recommendationMapper = recommendationMapper;
        this.recommendationFeedbackMapper = recommendationFeedbackMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.auditLogMapper = auditLogMapper;
        this.merchantMapper = merchantMapper;
    }

    public Map<String, Object> getOverview(String timeRange, String date, String startDate, String endDate, String month) {
        OffsetDateTime startTime = getStartTime(timeRange, date, startDate, endDate, month);
        OffsetDateTime endTime = getEndTime(timeRange, date, startDate, endDate, month);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeRange", timeRange);
        result.put("startTime", startTime);
        result.put("endTime", endTime);

        Map<String, Object> metrics = new LinkedHashMap<>();

        metrics.put("activeUsers", getActiveUsers(startTime, endTime));
        metrics.put("totalUsers", getTotalUsers());
        metrics.put("storeConsultations", getStoreConsultations(startTime, endTime));
        metrics.put("semanticSearches", getSemanticSearches(startTime, endTime));
        metrics.put("recommendationClicks", getRecommendationClicks(startTime, endTime));
        metrics.put("merchantCount", getMerchantCount());
        metrics.put("reviewCount", getReviewCount(startTime, endTime));
        metrics.put("totalReviews", getTotalReviews());
        metrics.put("aiCallCount", getAiCallCount(startTime, endTime));
        metrics.put("merchantActions", getMerchantActions(startTime, endTime));

        result.put("metrics", metrics);

        return result;
    }

    public Map<String, Object> getTrends(String timeRange, String date, String startDate, String endDate, String month) {
        OffsetDateTime startTime = getStartTime(timeRange, date, startDate, endDate, month);
        OffsetDateTime endTime = getEndTime(timeRange, date, startDate, endDate, month);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeRange", timeRange);
        result.put("labels", generateTimeLabels(timeRange, date, startDate, month));

        Map<String, List<Long>> trends = new LinkedHashMap<>();
        trends.put("activeUsers", getActiveUserTrend(timeRange, date, startDate, month));
        trends.put("reviews", getReviewTrend(timeRange, date, startDate, month));
        trends.put("aiCalls", getAiCallTrend(timeRange, date, startDate, month));
        trends.put("recommendations", getRecommendationTrend(timeRange, date, startDate, month));

        result.put("trends", trends);

        return result;
    }

    private OffsetDateTime getStartTime(String timeRange, String date, String startDate, String endDate, String month) {
        if ("day".equals(timeRange) && date != null && !date.isBlank()) {
            LocalDate selected = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            return selected.atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        if ("week".equals(timeRange) && startDate != null && !startDate.isBlank()) {
            LocalDate selected = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE);
            return selected.atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        if ("month".equals(timeRange) && month != null && !month.isBlank()) {
            LocalDate selected = LocalDate.parse(month + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
            return selected.atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        
        LocalDateTime localStart;
        switch (timeRange) {
            case "day":
                localStart = LocalDateTime.now().minusDays(1);
                break;
            case "month":
                localStart = LocalDateTime.now().minusMonths(1);
                break;
            case "week":
            default:
                localStart = LocalDateTime.now().minusWeeks(1);
                break;
        }
        return localStart.atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime getEndTime(String timeRange, String date, String startDate, String endDate, String month) {
        if ("day".equals(timeRange) && date != null && !date.isBlank()) {
            LocalDate selected = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            return selected.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
        }
        if ("week".equals(timeRange)) {
            if (endDate != null && !endDate.isBlank()) {
                LocalDate selected = LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE);
                return selected.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
            }
            if (startDate != null && !startDate.isBlank()) {
                LocalDate selected = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE);
                return selected.plusDays(6).atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
            }
        }
        if ("month".equals(timeRange) && month != null && !month.isBlank()) {
            LocalDate selected = LocalDate.parse(month + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
            return selected.plusMonths(1).minusDays(1).atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
        }
        
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private List<String> generateTimeLabels(String timeRange, String date, String startDate, String month) {
        List<String> labels = new ArrayList<>();
        LocalDateTime baseTime;
        
        if ("day".equals(timeRange) && date != null && !date.isBlank()) {
            baseTime = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } else if ("month".equals(timeRange) && month != null && !month.isBlank()) {
            baseTime = LocalDate.parse(month + "-01", DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } else if ("week".equals(timeRange) && startDate != null && !startDate.isBlank()) {
            baseTime = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } else if ("week".equals(timeRange) && date != null && !date.isBlank()) {
            baseTime = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } else {
            baseTime = LocalDateTime.now();
        }
        
        switch (timeRange) {
            case "day":
                for (int i = 0; i < 24; i++) {
                    labels.add(String.format("%02d:00", baseTime.plusHours(i).getHour()));
                }
                break;
            case "month":
                int daysInMonth = baseTime.toLocalDate().lengthOfMonth();
                for (int i = daysInMonth - 1; i >= 0; i--) {
                    LocalDateTime d = baseTime.plusDays(i);
                    labels.add(String.format("%d/%d", d.getMonthValue(), d.getDayOfMonth()));
                }
                break;
            case "week":
            default:
                for (int i = 0; i <= 6; i++) {
                    LocalDateTime d = baseTime.plusDays(i);
                    String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
                    labels.add(weekDays[d.getDayOfWeek().getValue() % 7]);
                }
                break;
        }
        return labels;
    }

    private Long getActiveUsers(OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return userMapper.countActiveUsers(startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getStoreConsultations(OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return chatSessionMapper.countByTimeRange(startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getSemanticSearches(OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return aiCallLogMapper.countByFunctionTypeAndTime("SEMANTIC_SEARCH", startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getRecommendationClicks(OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return recommendationFeedbackMapper.countByTimeRange(startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getMerchantCount() {
        try {
            return merchantMapper.selectCount(null);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getTotalUsers() {
        try {
            return userMapper.countTotalUsers();
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getTotalReviews() {
        try {
            return reviewMapper.countTotalReviews();
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getReviewCount(OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return reviewMapper.countByTimeRange(startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getAiCallCount(OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return aiCallLogMapper.countByTimeRange(startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Map<String, Long> getMerchantActions(OffsetDateTime startTime, OffsetDateTime endTime) {
        Map<String, Long> actions = new LinkedHashMap<>();
        try {
            actions.put("loginCount", auditLogMapper.countByOperatorRoleAndOperationType("MERCHANT", "LOGIN", startTime, endTime));
            actions.put("replyCount", auditLogMapper.countByOperatorRoleAndOperationType("MERCHANT", "REPLY_REVIEW", startTime, endTime));
            actions.put("updateCount", auditLogMapper.countByOperatorRoleAndOperationType("MERCHANT", "UPDATE_PROFILE", startTime, endTime));
            actions.put("viewStatsCount", auditLogMapper.countByOperatorRoleAndOperationType("MERCHANT", "VIEW_STATS", startTime, endTime));
        } catch (Exception e) {
            actions.put("loginCount", 0L);
            actions.put("replyCount", 0L);
            actions.put("updateCount", 0L);
            actions.put("viewStatsCount", 0L);
        }
        return actions;
    }

    private LocalDateTime getBaseTime(String timeRange, String date, String startDate, String month) {
        if ("day".equals(timeRange) && date != null && !date.isBlank()) {
            return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
        if ("month".equals(timeRange) && month != null && !month.isBlank()) {
            return LocalDate.parse(month + "-01", DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
        if ("week".equals(timeRange) && startDate != null && !startDate.isBlank()) {
            return LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
        if ("week".equals(timeRange) && date != null && !date.isBlank()) {
            return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
        return LocalDateTime.now();
    }

    private List<Long> getActiveUserTrend(String timeRange, String date, String startDate, String month) {
        List<Long> trend = new ArrayList<>();
        LocalDateTime baseTime = getBaseTime(timeRange, date, startDate, month);
        
        try {
            switch (timeRange) {
                case "day":
                    for (int i = 0; i < 24; i++) {
                        OffsetDateTime start = baseTime.plusHours(i).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusHours(i + 1).atOffset(ZoneOffset.UTC);
                        trend.add(userMapper.countActiveUsers(start, end));
                    }
                    break;
                case "month":
                    int daysInMonth = baseTime.toLocalDate().lengthOfMonth();
                    for (int i = daysInMonth - 1; i >= 0; i--) {
                        OffsetDateTime start = baseTime.plusDays(i).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusDays(i + 1).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        trend.add(userMapper.countActiveUsers(start, end));
                    }
                    break;
                case "week":
                default:
                    for (int i = 0; i <= 6; i++) {
                        OffsetDateTime start = baseTime.plusDays(i).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusDays(i + 1).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        trend.add(userMapper.countActiveUsers(start, end));
                    }
                    break;
            }
        } catch (Exception e) {
            int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? baseTime.toLocalDate().lengthOfMonth() : 7;
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }

    private List<Long> getReviewTrend(String timeRange, String date, String startDate, String month) {
        List<Long> trend = new ArrayList<>();
        LocalDateTime baseTime = getBaseTime(timeRange, date, startDate, month);
        
        try {
            switch (timeRange) {
                case "day":
                    for (int i = 0; i < 24; i++) {
                        OffsetDateTime start = baseTime.plusHours(i).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusHours(i + 1).atOffset(ZoneOffset.UTC);
                        trend.add(reviewMapper.countByTimeRange(start, end));
                    }
                    break;
                case "month":
                    int daysInMonth = baseTime.toLocalDate().lengthOfMonth();
                    for (int i = daysInMonth - 1; i >= 0; i--) {
                        OffsetDateTime start = baseTime.plusDays(i).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusDays(i + 1).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        trend.add(reviewMapper.countByTimeRange(start, end));
                    }
                    break;
                case "week":
                default:
                    for (int i = 0; i <= 6; i++) {
                        OffsetDateTime start = baseTime.plusDays(i).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusDays(i + 1).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        trend.add(reviewMapper.countByTimeRange(start, end));
                    }
                    break;
            }
        } catch (Exception e) {
            int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? baseTime.toLocalDate().lengthOfMonth() : 7;
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }

    private List<Long> getAiCallTrend(String timeRange, String date, String startDate, String month) {
        List<Long> trend = new ArrayList<>();
        LocalDateTime baseTime = getBaseTime(timeRange, date, startDate, month);
        
        try {
            switch (timeRange) {
                case "day":
                    for (int i = 0; i < 24; i++) {
                        OffsetDateTime start = baseTime.plusHours(i).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusHours(i + 1).atOffset(ZoneOffset.UTC);
                        trend.add(aiCallLogMapper.countByTimeRange(start, end));
                    }
                    break;
                case "month":
                    int daysInMonth = baseTime.toLocalDate().lengthOfMonth();
                    for (int i = daysInMonth - 1; i >= 0; i--) {
                        OffsetDateTime start = baseTime.plusDays(i).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusDays(i + 1).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        trend.add(aiCallLogMapper.countByTimeRange(start, end));
                    }
                    break;
                case "week":
                default:
                    for (int i = 0; i <= 6; i++) {
                        OffsetDateTime start = baseTime.plusDays(i).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusDays(i + 1).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        trend.add(aiCallLogMapper.countByTimeRange(start, end));
                    }
                    break;
            }
        } catch (Exception e) {
            int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? baseTime.toLocalDate().lengthOfMonth() : 7;
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }

    private List<Long> getRecommendationTrend(String timeRange, String date, String startDate, String month) {
        List<Long> trend = new ArrayList<>();
        LocalDateTime baseTime = getBaseTime(timeRange, date, startDate, month);
        
        try {
            switch (timeRange) {
                case "day":
                    for (int i = 0; i < 24; i++) {
                        OffsetDateTime start = baseTime.plusHours(i).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusHours(i + 1).atOffset(ZoneOffset.UTC);
                        trend.add(recommendationMapper.countByTimeRange(start, end));
                    }
                    break;
                case "month":
                    int daysInMonth = baseTime.toLocalDate().lengthOfMonth();
                    for (int i = daysInMonth - 1; i >= 0; i--) {
                        OffsetDateTime start = baseTime.plusDays(i).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusDays(i + 1).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        trend.add(recommendationMapper.countByTimeRange(start, end));
                    }
                    break;
                case "week":
                default:
                    for (int i = 0; i <= 6; i++) {
                        OffsetDateTime start = baseTime.plusDays(i).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        OffsetDateTime end = baseTime.plusDays(i + 1).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                        trend.add(recommendationMapper.countByTimeRange(start, end));
                    }
                    break;
            }
        } catch (Exception e) {
            int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? baseTime.toLocalDate().lengthOfMonth() : 7;
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }
}
