package com.foodadvisor.service;

import com.foodadvisor.mapper.MerchantStatisticsMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
public class MerchantStatisticsService {

    private final MerchantStatisticsMapper statisticsMapper;

    public MerchantStatisticsService(MerchantStatisticsMapper statisticsMapper) {
        this.statisticsMapper = statisticsMapper;
    }

    public Map<String, Object> getOverview(String timeRange, String date, String week, String month, String role, Long merchantId) {
        OffsetDateTime startTime = getStartTime(timeRange, date, week, month);
        OffsetDateTime endTime = getEndTime(timeRange, date, week, month);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeRange", timeRange);
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        result.put("role", role);
        result.put("merchantId", merchantId);

        Map<String, Object> metrics = new LinkedHashMap<>();

        if ("ADMIN".equalsIgnoreCase(role)) {
            metrics.put("totalMerchants", getTotalMerchants());
            metrics.put("activeMerchants", getActiveMerchants(startTime, endTime));
            metrics.put("totalReviews", getTotalPublishedReviews());
            metrics.put("repliedReviews", getTotalRepliedReviews());
            metrics.put("replyRate", getOverallReplyRate());
            metrics.put("reputationAnalysisCalls", getReputationAnalysisCalls(startTime, endTime));
            metrics.put("competitorAnalysisCalls", getCompetitorAnalysisCalls(startTime, endTime));
            metrics.put("businessAdviceCalls", getBusinessAdviceCalls(startTime, endTime));
        } else {
            metrics.put("totalMerchants", 1L);
            metrics.put("activeMerchants", getActiveMerchantById(merchantId, startTime, endTime));
            metrics.put("totalReviews", getPublishedReviewsByMerchant(merchantId));
            metrics.put("repliedReviews", getRepliedReviewsByMerchant(merchantId));
            metrics.put("replyRate", getMerchantReplyRate(merchantId));
            metrics.put("reputationAnalysisCalls", getReputationAnalysisCallsByMerchant(merchantId, startTime, endTime));
            metrics.put("competitorAnalysisCalls", getCompetitorAnalysisCallsByMerchant(merchantId, startTime, endTime));
            metrics.put("businessAdviceCalls", getBusinessAdviceCallsByMerchant(merchantId, startTime, endTime));
        }

        result.put("metrics", metrics);

        return result;
    }

    public Map<String, Object> getTrends(String timeRange, String date, String week, String month, String role, Long merchantId) {
        OffsetDateTime startTime = getStartTime(timeRange, date, week, month);
        OffsetDateTime endTime = getEndTime(timeRange, date, week, month);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeRange", timeRange);
        result.put("labels", generateTimeLabels(timeRange, date, week, month));

        Map<String, List<Long>> trends = new LinkedHashMap<>();

        if ("ADMIN".equalsIgnoreCase(role)) {
            trends.put("activeMerchants", getActiveMerchantTrend(timeRange, date, week, month, startTime, endTime));
            trends.put("reputationAnalysis", getReputationAnalysisTrend(timeRange, date, week, month, startTime, endTime));
            trends.put("competitorAnalysis", getCompetitorAnalysisTrend(timeRange, date, week, month, startTime, endTime));
            trends.put("businessAdvice", getBusinessAdviceTrend(timeRange, date, week, month, startTime, endTime));
        } else {
            trends.put("activeMerchants", getSingleMerchantActiveTrend(timeRange, date, week, month, merchantId, startTime, endTime));
            trends.put("reputationAnalysis", getSingleMerchantReputationTrend(timeRange, date, week, month, merchantId, startTime, endTime));
            trends.put("competitorAnalysis", getSingleMerchantCompetitorTrend(timeRange, date, week, month, merchantId, startTime, endTime));
            trends.put("businessAdvice", getSingleMerchantBusinessAdviceTrend(timeRange, date, week, month, merchantId, startTime, endTime));
        }

        result.put("trends", trends);

        return result;
    }

    private OffsetDateTime getStartTime(String timeRange, String date, String week, String month) {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        
        switch (timeRange) {
            case "day":
                if (date != null && !date.isEmpty()) {
                    try {
                        LocalDate localDate = LocalDate.parse(date);
                        return localDate.atStartOfDay().atOffset(ZoneOffset.UTC);
                    } catch (DateTimeParseException e) {
                        return nowUtc.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
                    }
                }
                return nowUtc.minusDays(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
                
            case "month":
                if (month != null && !month.isEmpty()) {
                    try {
                        YearMonth yearMonth = YearMonth.parse(month);
                        return yearMonth.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    } catch (DateTimeParseException e) {
                        return YearMonth.from(nowUtc).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    }
                }
                return YearMonth.from(nowUtc.minusMonths(1)).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                
            case "week":
            default:
                if (week != null && !week.isEmpty()) {
                    try {
                        String[] parts = week.split("-W");
                        if (parts.length == 2) {
                            int year = Integer.parseInt(parts[0]);
                            int weekNum = Integer.parseInt(parts[1]);
                            WeekFields weekFields = WeekFields.ISO;
                            LocalDate monday = LocalDate.of(year, 1, 1)
                                    .with(weekFields.weekOfYear(), weekNum)
                                    .with(weekFields.dayOfWeek(), 1);
                            return monday.atStartOfDay().atOffset(ZoneOffset.UTC);
                        }
                    } catch (Exception e) {
                        // fall through
                    }
                }
                LocalDateTime now = nowUtc.toLocalDateTime();
                LocalDateTime monday = now.minusDays(now.getDayOfWeek().getValue() - 1);
                return monday.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        }
    }

    private OffsetDateTime getEndTime(String timeRange, String date, String week, String month) {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        
        switch (timeRange) {
            case "day":
                if (date != null && !date.isEmpty()) {
                    try {
                        LocalDate localDate = LocalDate.parse(date);
                        return localDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    } catch (DateTimeParseException e) {
                        return nowUtc.toLocalDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    }
                }
                return nowUtc.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
                
            case "month":
                if (month != null && !month.isEmpty()) {
                    try {
                        YearMonth yearMonth = YearMonth.parse(month);
                        return yearMonth.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    } catch (DateTimeParseException e) {
                        return YearMonth.from(nowUtc).plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    }
                }
                return YearMonth.from(nowUtc).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                
            case "week":
            default:
                if (week != null && !week.isEmpty()) {
                    try {
                        String[] parts = week.split("-W");
                        if (parts.length == 2) {
                            int year = Integer.parseInt(parts[0]);
                            int weekNum = Integer.parseInt(parts[1]);
                            WeekFields weekFields = WeekFields.ISO;
                            LocalDate monday = LocalDate.of(year, 1, 1)
                                    .with(weekFields.weekOfYear(), weekNum)
                                    .with(weekFields.dayOfWeek(), 1);
                            return monday.plusDays(7).atStartOfDay().atOffset(ZoneOffset.UTC);
                        }
                    } catch (Exception e) {
                        // fall through
                    }
                }
                LocalDateTime now = nowUtc.toLocalDateTime();
                LocalDateTime monday = now.minusDays(now.getDayOfWeek().getValue() - 1);
                return monday.plusDays(7).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        }
    }

    private List<String> generateTimeLabels(String timeRange, String date, String week, String month) {
        List<String> labels = new ArrayList<>();
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDateTime now = nowUtc.toLocalDateTime();
        
        switch (timeRange) {
            case "day":
                LocalDate targetDate;
                if (date != null && !date.isEmpty()) {
                    try {
                        targetDate = LocalDate.parse(date);
                    } catch (DateTimeParseException e) {
                        targetDate = nowUtc.toLocalDate().minusDays(1);
                    }
                } else {
                    targetDate = nowUtc.toLocalDate().minusDays(1);
                }
                for (int i = 0; i < 24; i++) {
                    labels.add(String.format("%02d:00", i));
                }
                break;
                
            case "month":
                YearMonth targetMonth;
                if (month != null && !month.isEmpty()) {
                    try {
                        targetMonth = YearMonth.parse(month);
                    } catch (DateTimeParseException e) {
                        targetMonth = YearMonth.from(nowUtc);
                    }
                } else {
                    targetMonth = YearMonth.from(nowUtc);
                }
                int daysInMonth = targetMonth.lengthOfMonth();
                for (int i = 1; i <= daysInMonth; i++) {
                    labels.add(String.format("%d/%d", targetMonth.getMonthValue(), i));
                }
                break;
                
            case "week":
            default:
                LocalDate startOfWeek;
                if (week != null && !week.isEmpty()) {
                    try {
                        String[] parts = week.split("-W");
                        if (parts.length == 2) {
                            int year = Integer.parseInt(parts[0]);
                            int weekNum = Integer.parseInt(parts[1]);
                            WeekFields weekFields = WeekFields.ISO;
                            startOfWeek = LocalDate.of(year, 1, 1)
                                    .with(weekFields.weekOfYear(), weekNum)
                                    .with(weekFields.dayOfWeek(), 1);
                        } else {
                            startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                        }
                    } catch (Exception e) {
                        startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                    }
                } else {
                    startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                }
                String[] weekDays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
                for (int i = 0; i <= 6; i++) {
                    labels.add(weekDays[i]);
                }
                break;
        }
        return labels;
    }

    private Long getTotalMerchants() {
        try {
            return statisticsMapper.countTotalMerchants();
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getTotalPublishedReviews() {
        try {
            return statisticsMapper.countTotalPublishedReviews();
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getTotalRepliedReviews() {
        try {
            return statisticsMapper.countTotalRepliedReviews();
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getPublishedReviewsByMerchant(Long merchantId) {
        try {
            return statisticsMapper.countPublishedReviewsByMerchant(merchantId);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getRepliedReviewsByMerchant(Long merchantId) {
        try {
            return statisticsMapper.countRepliedReviewsByMerchant(merchantId);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getActiveMerchants(OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return statisticsMapper.countActiveMerchants(startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getActiveMerchantById(Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return statisticsMapper.countActiveMerchantById(merchantId, startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Double getOverallReplyRate() {
        try {
            Long totalReviews = statisticsMapper.countTotalPublishedReviews();
            Long repliedReviews = statisticsMapper.countTotalRepliedReviews();
            if (totalReviews == null || totalReviews == 0) {
                return 0.0;
            }
            return (repliedReviews * 100.0) / totalReviews;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Double getMerchantReplyRate(Long merchantId) {
        try {
            Long totalReviews = statisticsMapper.countPublishedReviewsByMerchant(merchantId);
            Long repliedReviews = statisticsMapper.countRepliedReviewsByMerchant(merchantId);
            if (totalReviews == null || totalReviews == 0) {
                return 0.0;
            }
            return (repliedReviews * 100.0) / totalReviews;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Long getReputationAnalysisCalls(OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return statisticsMapper.countReputationAnalysisCalls(startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getReputationAnalysisCallsByMerchant(Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return statisticsMapper.countReputationAnalysisCallsByMerchant(merchantId, startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getCompetitorAnalysisCalls(OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return statisticsMapper.countCompetitorAnalysisCalls(startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getCompetitorAnalysisCallsByMerchant(Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return statisticsMapper.countCompetitorAnalysisCallsByMerchant(merchantId, startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getBusinessAdviceCalls(OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return statisticsMapper.countBusinessAdviceCalls(startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long getBusinessAdviceCallsByMerchant(Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        try {
            return statisticsMapper.countBusinessAdviceCallsByMerchant(merchantId, startTime, endTime);
        } catch (Exception e) {
            return 0L;
        }
    }

    private List<Long> getActiveMerchantTrend(String timeRange, String date, String week, String month, 
            OffsetDateTime startTime, OffsetDateTime endTime) {
        List<Long> trend = new ArrayList<>();
        
        try {
            List<Map<String, Object>> rawData = statisticsMapper.getActiveMerchantTrend(startTime, endTime);
            Map<String, Long> dataMap = new HashMap<>();
            
            for (Map<String, Object> row : rawData) {
                String dateStr = row.get("date").toString();
                dataMap.put(dateStr.substring(0, 10), ((Number) row.get("count")).longValue());
            }
            
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            
            if ("week".equals(timeRange)) {
                LocalDate startOfWeek;
                if (week != null && !week.isEmpty()) {
                    try {
                        String[] parts = week.split("-W");
                        if (parts.length == 2) {
                            int year = Integer.parseInt(parts[0]);
                            int weekNum = Integer.parseInt(parts[1]);
                            WeekFields weekFields = WeekFields.ISO;
                            startOfWeek = LocalDate.of(year, 1, 1)
                                    .with(weekFields.weekOfYear(), weekNum)
                                    .with(weekFields.dayOfWeek(), 1);
                        } else {
                            startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                        }
                    } catch (Exception e) {
                        startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                    }
                } else {
                    startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                }
                for (int i = 0; i <= 6; i++) {
                    String key = startOfWeek.plusDays(i).toString();
                    trend.add(dataMap.getOrDefault(key, 0L));
                }
            } else if ("day".equals(timeRange)) {
                LocalDate targetDate;
                if (date != null && !date.isEmpty()) {
                    try {
                        targetDate = LocalDate.parse(date);
                    } catch (DateTimeParseException e) {
                        targetDate = nowUtc.toLocalDate().minusDays(1);
                    }
                } else {
                    targetDate = nowUtc.toLocalDate().minusDays(1);
                }
                for (int i = 0; i < 24; i++) {
                    String key = targetDate.toString();
                    trend.add(dataMap.getOrDefault(key, 0L));
                }
            } else {
                YearMonth targetMonth;
                if (month != null && !month.isEmpty()) {
                    try {
                        targetMonth = YearMonth.parse(month);
                    } catch (DateTimeParseException e) {
                        targetMonth = YearMonth.from(nowUtc);
                    }
                } else {
                    targetMonth = YearMonth.from(nowUtc);
                }
                int daysInMonth = targetMonth.lengthOfMonth();
                for (int i = 1; i <= daysInMonth; i++) {
                    String key = targetMonth.atDay(i).toString();
                    trend.add(dataMap.getOrDefault(key, 0L));
                }
            }
        } catch (Exception e) {
            int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? 30 : 7;
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }

    private List<Long> getReputationAnalysisTrend(String timeRange, String date, String week, String month,
            OffsetDateTime startTime, OffsetDateTime endTime) {
        return getModuleTrend(timeRange, date, week, month, statisticsMapper.getReputationAnalysisTrend(startTime, endTime));
    }

    private List<Long> getCompetitorAnalysisTrend(String timeRange, String date, String week, String month,
            OffsetDateTime startTime, OffsetDateTime endTime) {
        return getModuleTrend(timeRange, date, week, month, statisticsMapper.getCompetitorAnalysisTrend(startTime, endTime));
    }

    private List<Long> getBusinessAdviceTrend(String timeRange, String date, String week, String month,
            OffsetDateTime startTime, OffsetDateTime endTime) {
        return getModuleTrend(timeRange, date, week, month, statisticsMapper.getBusinessAdviceTrend(startTime, endTime));
    }

    private List<Long> getModuleTrend(String timeRange, String date, String week, String month, 
            List<Map<String, Object>> rawData) {
        List<Long> trend = new ArrayList<>();
        
        try {
            Map<String, Long> dataMap = new HashMap<>();
            for (Map<String, Object> row : rawData) {
                String dateStr = row.get("date").toString();
                dataMap.put(dateStr.substring(0, 10), ((Number) row.get("count")).longValue());
            }
            
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            
            if ("week".equals(timeRange)) {
                LocalDate startOfWeek;
                if (week != null && !week.isEmpty()) {
                    try {
                        String[] parts = week.split("-W");
                        if (parts.length == 2) {
                            int year = Integer.parseInt(parts[0]);
                            int weekNum = Integer.parseInt(parts[1]);
                            WeekFields weekFields = WeekFields.ISO;
                            startOfWeek = LocalDate.of(year, 1, 1)
                                    .with(weekFields.weekOfYear(), weekNum)
                                    .with(weekFields.dayOfWeek(), 1);
                        } else {
                            startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                        }
                    } catch (Exception e) {
                        startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                    }
                } else {
                    startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                }
                for (int i = 0; i <= 6; i++) {
                    String key = startOfWeek.plusDays(i).toString();
                    trend.add(dataMap.getOrDefault(key, 0L));
                }
            } else if ("day".equals(timeRange)) {
                LocalDate targetDate;
                if (date != null && !date.isEmpty()) {
                    try {
                        targetDate = LocalDate.parse(date);
                    } catch (DateTimeParseException e) {
                        targetDate = nowUtc.toLocalDate().minusDays(1);
                    }
                } else {
                    targetDate = nowUtc.toLocalDate().minusDays(1);
                }
                for (int i = 0; i < 24; i++) {
                    String key = targetDate.toString();
                    trend.add(dataMap.getOrDefault(key, 0L));
                }
            } else {
                YearMonth targetMonth;
                if (month != null && !month.isEmpty()) {
                    try {
                        targetMonth = YearMonth.parse(month);
                    } catch (DateTimeParseException e) {
                        targetMonth = YearMonth.from(nowUtc);
                    }
                } else {
                    targetMonth = YearMonth.from(nowUtc);
                }
                int daysInMonth = targetMonth.lengthOfMonth();
                for (int i = 1; i <= daysInMonth; i++) {
                    String key = targetMonth.atDay(i).toString();
                    trend.add(dataMap.getOrDefault(key, 0L));
                }
            }
        } catch (Exception e) {
            int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? 30 : 7;
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }

    private List<Long> getSingleMerchantActiveTrend(String timeRange, String date, String week, String month,
            Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        List<Long> trend = new ArrayList<>();
        
        try {
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            
            if ("week".equals(timeRange)) {
                LocalDate startOfWeek;
                if (week != null && !week.isEmpty()) {
                    try {
                        String[] parts = week.split("-W");
                        if (parts.length == 2) {
                            int year = Integer.parseInt(parts[0]);
                            int weekNum = Integer.parseInt(parts[1]);
                            WeekFields weekFields = WeekFields.ISO;
                            startOfWeek = LocalDate.of(year, 1, 1)
                                    .with(weekFields.weekOfYear(), weekNum)
                                    .with(weekFields.dayOfWeek(), 1);
                        } else {
                            startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                        }
                    } catch (Exception e) {
                        startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                    }
                } else {
                    startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                }
                for (int i = 0; i <= 6; i++) {
                    OffsetDateTime start = startOfWeek.plusDays(i).atStartOfDay().atOffset(ZoneOffset.UTC);
                    OffsetDateTime end = startOfWeek.plusDays(i + 1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    Long count = statisticsMapper.countActiveMerchantById(merchantId, start, end);
                    trend.add(count != null && count > 0 ? 1L : 0L);
                }
            } else if ("day".equals(timeRange)) {
                LocalDate targetDate;
                if (date != null && !date.isEmpty()) {
                    try {
                        targetDate = LocalDate.parse(date);
                    } catch (DateTimeParseException e) {
                        targetDate = nowUtc.toLocalDate().minusDays(1);
                    }
                } else {
                    targetDate = nowUtc.toLocalDate().minusDays(1);
                }
                for (int i = 0; i < 24; i++) {
                    OffsetDateTime start = targetDate.atTime(i, 0).atOffset(ZoneOffset.UTC);
                    OffsetDateTime end = targetDate.atTime(i + 1, 0).atOffset(ZoneOffset.UTC);
                    Long count = statisticsMapper.countActiveMerchantById(merchantId, start, end);
                    trend.add(count != null && count > 0 ? 1L : 0L);
                }
            } else {
                YearMonth targetMonth;
                if (month != null && !month.isEmpty()) {
                    try {
                        targetMonth = YearMonth.parse(month);
                    } catch (DateTimeParseException e) {
                        targetMonth = YearMonth.from(nowUtc);
                    }
                } else {
                    targetMonth = YearMonth.from(nowUtc);
                }
                int daysInMonth = targetMonth.lengthOfMonth();
                for (int i = 1; i <= daysInMonth; i++) {
                    OffsetDateTime start = targetMonth.atDay(i).atStartOfDay().atOffset(ZoneOffset.UTC);
                    OffsetDateTime end = targetMonth.atDay(i + 1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    Long count = statisticsMapper.countActiveMerchantById(merchantId, start, end);
                    trend.add(count != null && count > 0 ? 1L : 0L);
                }
            }
        } catch (Exception e) {
            int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? 30 : 7;
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }

    private List<Long> getSingleMerchantReputationTrend(String timeRange, String date, String week, String month,
            Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        return getSingleMerchantModuleTrend(timeRange, date, week, month, merchantId, startTime, endTime, true);
    }

    private List<Long> getSingleMerchantCompetitorTrend(String timeRange, String date, String week, String month,
            Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        return getSingleMerchantModuleTrend(timeRange, date, week, month, merchantId, startTime, endTime, false);
    }

    private List<Long> getSingleMerchantBusinessAdviceTrend(String timeRange, String date, String week, String month,
            Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        return getSingleMerchantModuleTrend(timeRange, date, week, month, merchantId, startTime, endTime, false);
    }

    private List<Long> getSingleMerchantModuleTrend(String timeRange, String date, String week, String month,
            Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime, boolean isReputation) {
        List<Long> trend = new ArrayList<>();
        
        try {
            OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
            
            if ("week".equals(timeRange)) {
                LocalDate startOfWeek;
                if (week != null && !week.isEmpty()) {
                    try {
                        String[] parts = week.split("-W");
                        if (parts.length == 2) {
                            int year = Integer.parseInt(parts[0]);
                            int weekNum = Integer.parseInt(parts[1]);
                            WeekFields weekFields = WeekFields.ISO;
                            startOfWeek = LocalDate.of(year, 1, 1)
                                    .with(weekFields.weekOfYear(), weekNum)
                                    .with(weekFields.dayOfWeek(), 1);
                        } else {
                            startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                        }
                    } catch (Exception e) {
                        startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                    }
                } else {
                    startOfWeek = nowUtc.toLocalDate().minusDays(nowUtc.getDayOfWeek().getValue() - 1);
                }
                for (int i = 0; i <= 6; i++) {
                    OffsetDateTime start = startOfWeek.plusDays(i).atStartOfDay().atOffset(ZoneOffset.UTC);
                    OffsetDateTime end = startOfWeek.plusDays(i + 1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    Long count;
                    if (isReputation) {
                        count = statisticsMapper.countReputationAnalysisCallsByMerchant(merchantId, start, end);
                    } else {
                        count = statisticsMapper.countCompetitorAnalysisCallsByMerchant(merchantId, start, end);
                    }
                    trend.add(count != null ? count : 0L);
                }
            } else if ("day".equals(timeRange)) {
                LocalDate targetDate;
                if (date != null && !date.isEmpty()) {
                    try {
                        targetDate = LocalDate.parse(date);
                    } catch (DateTimeParseException e) {
                        targetDate = nowUtc.toLocalDate().minusDays(1);
                    }
                } else {
                    targetDate = nowUtc.toLocalDate().minusDays(1);
                }
                for (int i = 0; i < 24; i++) {
                    OffsetDateTime start = targetDate.atTime(i, 0).atOffset(ZoneOffset.UTC);
                    OffsetDateTime end = targetDate.atTime(i + 1, 0).atOffset(ZoneOffset.UTC);
                    Long count;
                    if (isReputation) {
                        count = statisticsMapper.countReputationAnalysisCallsByMerchant(merchantId, start, end);
                    } else {
                        count = statisticsMapper.countCompetitorAnalysisCallsByMerchant(merchantId, start, end);
                    }
                    trend.add(count != null ? count : 0L);
                }
            } else {
                YearMonth targetMonth;
                if (month != null && !month.isEmpty()) {
                    try {
                        targetMonth = YearMonth.parse(month);
                    } catch (DateTimeParseException e) {
                        targetMonth = YearMonth.from(nowUtc);
                    }
                } else {
                    targetMonth = YearMonth.from(nowUtc);
                }
                int daysInMonth = targetMonth.lengthOfMonth();
                for (int i = 1; i <= daysInMonth; i++) {
                    OffsetDateTime start = targetMonth.atDay(i).atStartOfDay().atOffset(ZoneOffset.UTC);
                    OffsetDateTime end = targetMonth.atDay(i + 1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    Long count;
                    if (isReputation) {
                        count = statisticsMapper.countReputationAnalysisCallsByMerchant(merchantId, start, end);
                    } else {
                        count = statisticsMapper.countCompetitorAnalysisCallsByMerchant(merchantId, start, end);
                    }
                    trend.add(count != null ? count : 0L);
                }
            }
        } catch (Exception e) {
            int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? 30 : 7;
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }
}