package com.foodadvisor.service;

import com.foodadvisor.mapper.MerchantStatisticsMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MerchantStatisticsService {

    private final MerchantStatisticsMapper statisticsMapper;

    public MerchantStatisticsService(MerchantStatisticsMapper statisticsMapper) {
        this.statisticsMapper = statisticsMapper;
    }

    public Map<String, Object> getOverview(String timeRange, String role, Long merchantId) {
        OffsetDateTime startTime = getStartTime(timeRange);
        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);

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

    public Map<String, Object> getTrends(String timeRange, String role, Long merchantId) {
        OffsetDateTime startTime = getStartTime(timeRange);
        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeRange", timeRange);
        result.put("labels", generateTimeLabels(timeRange));

        Map<String, List<Long>> trends = new LinkedHashMap<>();

        if ("ADMIN".equalsIgnoreCase(role)) {
            trends.put("activeMerchants", getActiveMerchantTrend(timeRange, startTime, endTime));
            trends.put("reputationAnalysis", getReputationAnalysisTrend(timeRange, startTime, endTime));
            trends.put("competitorAnalysis", getCompetitorAnalysisTrend(timeRange, startTime, endTime));
            trends.put("businessAdvice", getBusinessAdviceTrend(timeRange, startTime, endTime));
        } else {
            trends.put("activeMerchants", getSingleMerchantActiveTrend(timeRange, merchantId, startTime, endTime));
            trends.put("reputationAnalysis", getSingleMerchantReputationTrend(timeRange, merchantId, startTime, endTime));
            trends.put("competitorAnalysis", getSingleMerchantCompetitorTrend(timeRange, merchantId, startTime, endTime));
            trends.put("businessAdvice", getSingleMerchantBusinessAdviceTrend(timeRange, merchantId, startTime, endTime));
        }

        result.put("trends", trends);

        return result;
    }

    private OffsetDateTime getStartTime(String timeRange) {
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

    private List<String> generateTimeLabels(String timeRange) {
        List<String> labels = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        switch (timeRange) {
            case "day":
                for (int i = 23; i >= 0; i--) {
                    labels.add(String.format("%02d:00", now.minusHours(i).getHour()));
                }
                break;
            case "month":
                for (int i = 29; i >= 0; i--) {
                    LocalDateTime d = now.minusDays(i);
                    labels.add(String.format("%d/%d", d.getMonthValue(), d.getDayOfMonth()));
                }
                break;
            case "week":
            default:
                for (int i = 6; i >= 0; i--) {
                    LocalDateTime d = now.minusDays(i);
                    String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
                    labels.add(weekDays[d.getDayOfWeek().getValue() % 7]);
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

    private List<Long> getActiveMerchantTrend(String timeRange, OffsetDateTime startTime, OffsetDateTime endTime) {
        List<Long> trend = new ArrayList<>();
        int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? 30 : 7;
        
        try {
            List<Map<String, Object>> rawData = statisticsMapper.getActiveMerchantTrend(startTime, endTime);
            Map<String, Long> dataMap = new HashMap<>();
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            
            for (Map<String, Object> row : rawData) {
                String date = row.get("date").toString();
                dataMap.put(date.substring(0, 10), ((Number) row.get("count")).longValue());
            }
            
            LocalDateTime now = LocalDateTime.now();
            for (int i = size - 1; i >= 0; i--) {
                LocalDateTime d;
                if (timeRange.equals("day")) {
                    d = now.minusHours(i);
                } else {
                    d = now.minusDays(i);
                }
                String key = d.toLocalDate().toString();
                trend.add(dataMap.getOrDefault(key, 0L));
            }
        } catch (Exception e) {
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }

    private List<Long> getReputationAnalysisTrend(String timeRange, OffsetDateTime startTime, OffsetDateTime endTime) {
        return getModuleTrend(timeRange, statisticsMapper.getReputationAnalysisTrend(startTime, endTime));
    }

    private List<Long> getCompetitorAnalysisTrend(String timeRange, OffsetDateTime startTime, OffsetDateTime endTime) {
        return getModuleTrend(timeRange, statisticsMapper.getCompetitorAnalysisTrend(startTime, endTime));
    }

    private List<Long> getBusinessAdviceTrend(String timeRange, OffsetDateTime startTime, OffsetDateTime endTime) {
        return getModuleTrend(timeRange, statisticsMapper.getBusinessAdviceTrend(startTime, endTime));
    }

    private List<Long> getModuleTrend(String timeRange, List<Map<String, Object>> rawData) {
        List<Long> trend = new ArrayList<>();
        int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? 30 : 7;
        
        try {
            Map<String, Long> dataMap = new HashMap<>();
            for (Map<String, Object> row : rawData) {
                String date = row.get("date").toString();
                dataMap.put(date.substring(0, 10), ((Number) row.get("count")).longValue());
            }
            
            LocalDateTime now = LocalDateTime.now();
            for (int i = size - 1; i >= 0; i--) {
                LocalDateTime d;
                if (timeRange.equals("day")) {
                    d = now.minusHours(i);
                } else {
                    d = now.minusDays(i);
                }
                String key = d.toLocalDate().toString();
                trend.add(dataMap.getOrDefault(key, 0L));
            }
        } catch (Exception e) {
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }

    private List<Long> getSingleMerchantActiveTrend(String timeRange, Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        List<Long> trend = new ArrayList<>();
        int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? 30 : 7;
        
        try {
            LocalDateTime now = LocalDateTime.now();
            for (int i = size - 1; i >= 0; i--) {
                OffsetDateTime start, end;
                if (timeRange.equals("day")) {
                    start = now.minusHours(i).atOffset(ZoneOffset.UTC);
                    end = now.minusHours(i - 1).atOffset(ZoneOffset.UTC);
                } else {
                    start = now.minusDays(i).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                    end = now.minusDays(i - 1).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                }
                Long count = statisticsMapper.countActiveMerchantById(merchantId, start, end);
                trend.add(count != null && count > 0 ? 1L : 0L);
            }
        } catch (Exception e) {
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }

    private List<Long> getSingleMerchantReputationTrend(String timeRange, Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        return getSingleMerchantModuleTrend(timeRange, merchantId, startTime, endTime, true);
    }

    private List<Long> getSingleMerchantCompetitorTrend(String timeRange, Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        return getSingleMerchantModuleTrend(timeRange, merchantId, startTime, endTime, false);
    }

    private List<Long> getSingleMerchantBusinessAdviceTrend(String timeRange, Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime) {
        return getSingleMerchantModuleTrend(timeRange, merchantId, startTime, endTime, false);
    }

    private List<Long> getSingleMerchantModuleTrend(String timeRange, Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime, boolean isReputation) {
        List<Long> trend = new ArrayList<>();
        int size = timeRange.equals("day") ? 24 : timeRange.equals("month") ? 30 : 7;
        
        try {
            LocalDateTime now = LocalDateTime.now();
            for (int i = size - 1; i >= 0; i--) {
                OffsetDateTime start, end;
                if (timeRange.equals("day")) {
                    start = now.minusHours(i).atOffset(ZoneOffset.UTC);
                    end = now.minusHours(i - 1).atOffset(ZoneOffset.UTC);
                } else {
                    start = now.minusDays(i).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                    end = now.minusDays(i - 1).with(LocalTime.MIN).atOffset(ZoneOffset.UTC);
                }
                Long count;
                if (isReputation) {
                    count = statisticsMapper.countReputationAnalysisCallsByMerchant(merchantId, start, end);
                } else {
                    count = statisticsMapper.countCompetitorAnalysisCallsByMerchant(merchantId, start, end);
                }
                trend.add(count != null ? count : 0L);
            }
        } catch (Exception e) {
            for (int i = 0; i < size; i++) {
                trend.add(0L);
            }
        }
        return trend;
    }
}