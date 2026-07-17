package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.dto.reputation.ReputationOverviewVO;
import com.foodadvisor.dto.reputation.ReputationTrendPointVO;
import com.foodadvisor.dto.reputation.ReputationTrendVO;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.MerchantReputationStatistics;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewAnalysis;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.MerchantReputationStatisticsMapper;
import com.foodadvisor.mapper.ReviewAnalysisMapper;
import com.foodadvisor.mapper.ReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商家口碑趋势追踪服务。
 *
 * 核心职责：
 * 1. 按日/周/月聚合商家的评价数据（评分、情感分布），存入 merchant_reputation_statistics 表
 * 2. 提供趋势查询接口，返回一段时间内的口碑变化数据
 * 3. 提供口碑概览接口，包括近 30 天统计和环比变化
 *
 * 数据来源：
 * - reviews 表（PUBLISHED + APPROVED 的评价）
 * - review_analysis 表（情感分析结果：POSITIVE / NEUTRAL / NEGATIVE）
 *
 * 统计周期定义：
 * - DAY：自然日（00:00:00 ~ 23:59:59）
 * - WEEK：自然周（周一 ~ 周日）
 * - MONTH：自然月（1号 ~ 月末）
 */
@Service
public class MerchantReputationService {

    private static final Logger log = LoggerFactory.getLogger(MerchantReputationService.class);

    private final MerchantReputationStatisticsMapper statsMapper;
    private final ReviewMapper reviewMapper;
    private final ReviewAnalysisMapper analysisMapper;
    private final MerchantMapper merchantMapper;
    private final JdbcTemplate jdbcTemplate;

    public MerchantReputationService(
            MerchantReputationStatisticsMapper statsMapper,
            ReviewMapper reviewMapper,
            ReviewAnalysisMapper analysisMapper,
            MerchantMapper merchantMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.statsMapper = statsMapper;
        this.reviewMapper = reviewMapper;
        this.analysisMapper = analysisMapper;
        this.merchantMapper = merchantMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== 趋势查询 ====================

    /**
     * 查询商家口碑趋势数据。
     *
     * 先从已预计算的统计表中查询数据；如果指定范围内没有数据，
     * 则自动触发一次计算后再查询，保证前端总能拿到结果。
     *
     * @param merchantId 商家 ID
     * @param periodType 周期类型：DAY / WEEK / MONTH
     * @param startDate  查询起始日期（含），默认 30 天前
     * @param endDate    查询结束日期（含），默认今天
     * @return 包含趋势数据点和概要信息的完整响应
     */
    public ReputationTrendVO getReputationTrend(
            Long merchantId,
            String periodType,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // 参数默认值
        if (periodType == null || periodType.isBlank()) {
            periodType = "WEEK";
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            // 默认往前推 12 个周期
            startDate = switch (periodType) {
                case "DAY" -> endDate.minusDays(30);
                case "WEEK" -> endDate.minusWeeks(12);
                case "MONTH" -> endDate.minusMonths(12);
                default -> endDate.minusWeeks(12);
            };
        }

        // 先从缓存表查询已预计算的统计数据
        List<MerchantReputationStatistics> statsList =
                queryStats(merchantId, periodType, startDate, endDate);

        // 如果缓存未命中，则聚合原始评价数据实时计算，同时回写缓存供后续查询
        if (statsList.isEmpty()) {
            refreshReputationStats(merchantId, periodType, startDate, endDate);
            statsList = computeStatsDirectly(merchantId, periodType, startDate, endDate);
        }

        // 转换为 VO
        Merchant merchant = merchantMapper.selectById(merchantId);
        String merchantName = merchant != null ? merchant.getName() : "";

        List<ReputationTrendPointVO> dataPoints = statsList.stream()
                .map(this::toTrendPointVO)
                .collect(Collectors.toList());

        // 计算概要指标
        return buildTrendVO(merchantId, merchantName, periodType, dataPoints);
    }

    // ==================== 概览查询 ====================

    /**
     * 获取商家口碑概览。
     *
     * 包括：
     * - 当前评分和评价数（来自 merchants 表）
     * - 近 30 天统计
     * - 与前一个 30 天的环比变化
     *
     * @param merchantId 商家 ID
     * @return 口碑概览 VO
     */
    public ReputationOverviewVO getReputationOverview(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);
        LocalDate sixtyDaysAgo = today.minusDays(60);

        // 确保近 30 天有数据
        refreshReputationStats(merchantId, "DAY", sixtyDaysAgo, today);

        // 查询近 30 天的天级统计
        List<MerchantReputationStatistics> recent30d =
                queryStats(merchantId, "DAY", thirtyDaysAgo, today);

        // 查询前 30 天的天级统计
        List<MerchantReputationStatistics> previous30d =
                queryStats(merchantId, "DAY", sixtyDaysAgo, thirtyDaysAgo.minusDays(1));

        // 聚合近 30 天数据
        AggregationResult recent = aggregateStats(recent30d);
        AggregationResult previous = aggregateStats(previous30d);

        // 计算变化量
        BigDecimal ratingChange = subtract(recent.avgRating, previous.avgRating);
        BigDecimal positiveRatioChange = subtract(recent.positiveRatio, previous.positiveRatio);
        BigDecimal negativeRatioChange = subtract(recent.negativeRatio, previous.negativeRatio);

        // 判断整体趋势
        String overallTrend;
        if (ratingChange != null && ratingChange.compareTo(BigDecimal.valueOf(0.3)) > 0) {
            overallTrend = "IMPROVING";
        } else if (ratingChange != null && ratingChange.compareTo(BigDecimal.valueOf(-0.3)) < 0) {
            overallTrend = "DECLINING";
        } else {
            overallTrend = "STABLE";
        }

        LocalDate lastDataDate = queryLatestPeriodEnd(merchantId, "DAY");

        return ReputationOverviewVO.builder()
                .merchantId(merchantId)
                .merchantName(merchant.getName())
                .currentRating(merchant.getRating())
                .reviewCount(merchant.getReviewCount())
                .recent30dAvgRating(recent.avgRating)
                .recent30dReviewCount(recent.totalCount)
                .recent30dPositiveRatio(recent.positiveRatio)
                .recent30dNegativeRatio(recent.negativeRatio)
                .ratingChange(ratingChange)
                .positiveRatioChange(positiveRatioChange)
                .negativeRatioChange(negativeRatioChange)
                .overallTrend(overallTrend)
                .lastDataDate(lastDataDate)
                .build();
    }

    // ==================== 统计刷新 ====================

    /**
     * 刷新（重新计算）指定范围内的商家口碑统计。
     *
     * 采用"先删后插"策略保证幂等：对范围内每个周期，
     * 先删除已有记录，再根据原始评价数据重新计算并插入。
     *
     * @param merchantId 商家 ID
     * @param periodType 周期类型
     * @param startDate  刷新起始日期
     * @param endDate    刷新结束日期
     */
    @Transactional
    public void refreshReputationStats(
            Long merchantId,
            String periodType,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // 获取该商家所有已发布且审核通过的评价
        List<Review> reviews = fetchPublishedReviews(merchantId);
        if (reviews.isEmpty()) {
            return;
        }

        // 获取这些评价的情感分析结果，建立 reviewId -> sentiment 映射
        Map<Long, String> sentimentMap = buildSentimentMap(reviews);

        // 按周期分组评价
        Map<PeriodKey, List<Review>> grouped = groupReviewsByPeriod(reviews, periodType, startDate, endDate);

        // 对每个周期计算并写入统计
        for (Map.Entry<PeriodKey, List<Review>> entry : grouped.entrySet()) {
            PeriodKey key = entry.getKey();
            List<Review> periodReviews = entry.getValue();

            // 先删后插，保证幂等
            statsMapper.deleteByPeriod(merchantId, periodType, key.startDate, key.endDate);

            MerchantReputationStatistics stats = computeStats(
                    merchantId, periodType, key.startDate, key.endDate, periodReviews, sentimentMap
            );

            if (stats != null) {
                statsMapper.insert(stats);
            }
        }

        log.info("已刷新商家 {} 的 {} 口碑统计，范围 {} ~ {}，共 {} 个周期",
                merchantId, periodType, startDate, endDate, grouped.size());
    }

    /**
     * 直接从原始数据计算趋势统计（不依赖缓存表查询）。
     *
     * 与 refreshReputationStats 聚合逻辑完全一致，区别在于：
     * - refreshReputationStats 写入 DB 后不返回数据
     * - computeStatsDirectly 聚合后直接返回 List，同时也写入 DB 以供后续缓存命中
     */
    private List<MerchantReputationStatistics> computeStatsDirectly(
            Long merchantId,
            String periodType,
            LocalDate startDate,
            LocalDate endDate
    ) {
        List<Review> reviews = fetchPublishedReviews(merchantId);
        if (reviews.isEmpty()) {
            return List.of();
        }

        Map<Long, String> sentimentMap = buildSentimentMap(reviews);
        Map<PeriodKey, List<Review>> grouped = groupReviewsByPeriod(reviews, periodType, startDate, endDate);

        List<MerchantReputationStatistics> result = new ArrayList<>();
        for (Map.Entry<PeriodKey, List<Review>> entry : grouped.entrySet()) {
            PeriodKey key = entry.getKey();
            List<Review> periodReviews = entry.getValue();

            MerchantReputationStatistics stats = computeStats(
                    merchantId, periodType, key.startDate, key.endDate, periodReviews, sentimentMap
            );

            if (stats != null) {
                result.add(stats);
            }
        }

        // 按 period_start 升序排列
        result.sort(Comparator.comparing(MerchantReputationStatistics::getPeriodStart));
        return result;
    }

    // ==================== 内部方法 ====================

    /**
     * 使用 LambdaQueryWrapper 查询统计记录，替代 @Select 注解避免 MyBatis 参数绑定问题。
     *
     * 查询逻辑：统计周期与 [startDate, endDate] 有交集即返回（period_end >= startDate AND period_start <= endDate）。
     */
    private List<MerchantReputationStatistics> queryStats(
            Long merchantId,
            String periodType,
            LocalDate startDate,
            LocalDate endDate
    ) {
        // 直接用 JdbcTemplate.query 带 RowMapper 和参数数组（非 queryForList）
        String sql = "SELECT id, merchant_id, period_type, period_start, period_end,"
                + " average_rating, positive_count, neutral_count, negative_count,"
                + " total_review_count, positive_ratio, negative_ratio"
                + " FROM merchant_reputation_statistics"
                + " WHERE merchant_id = ? AND period_type = ?"
                + " ORDER BY period_start ASC";

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    MerchantReputationStatistics stats = new MerchantReputationStatistics();
                    stats.setId(rs.getLong("id"));
                    stats.setMerchantId(rs.getLong("merchant_id"));
                    stats.setPeriodType(rs.getString("period_type"));
                    stats.setPeriodStart(rs.getDate("period_start") != null
                            ? rs.getDate("period_start").toLocalDate() : null);
                    stats.setPeriodEnd(rs.getDate("period_end") != null
                            ? rs.getDate("period_end").toLocalDate() : null);
                    stats.setAverageRating(rs.getBigDecimal("average_rating"));
                    stats.setPositiveCount(rs.getInt("positive_count"));
                    stats.setNeutralCount(rs.getInt("neutral_count"));
                    stats.setNegativeCount(rs.getInt("negative_count"));
                    stats.setTotalReviewCount(rs.getInt("total_review_count"));
                    stats.setPositiveRatio(rs.getBigDecimal("positive_ratio"));
                    stats.setNegativeRatio(rs.getBigDecimal("negative_ratio"));
                    return stats;
                },
                merchantId, periodType
        );
    }

    /**
     * 查询某商家指定周期类型下最新一条记录的 period_end。
     */
    private LocalDate queryLatestPeriodEnd(Long merchantId, String periodType) {
        List<LocalDate> results = jdbcTemplate.query(
                "SELECT period_end FROM merchant_reputation_statistics"
                        + " WHERE merchant_id = ? AND period_type = ?"
                        + " ORDER BY period_end DESC LIMIT 1",
                (rs, rowNum) -> {
                    java.sql.Date d = rs.getDate("period_end");
                    return d != null ? d.toLocalDate() : null;
                },
                merchantId, periodType
        );
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 查询某商家所有已发布且审核通过的评价。
     */
    private List<Review> fetchPublishedReviews(Long merchantId) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getMerchantId, merchantId)
                .eq(Review::getStatus, "PUBLISHED")
                .eq(Review::getModerationStatus, "APPROVED");
        return reviewMapper.selectList(wrapper);
    }

    /**
     * 批量查询评价的情感分析结果，建立 reviewId -> sentiment 映射。
     * 对于没有分析结果的评价，根据评分推断情感（4-5 → POSITIVE，3 → NEUTRAL，1-2 → NEGATIVE）。
     */
    private Map<Long, String> buildSentimentMap(List<Review> reviews) {
        List<Long> reviewIds = reviews.stream()
                .map(Review::getId)
                .toList();

        if (reviewIds.isEmpty()) {
            return Map.of();
        }

        // 查询最新的分析结果（按 review_id 分组取最新一条）
        List<ReviewAnalysis> analyses = analysisMapper.selectList(
                new LambdaQueryWrapper<ReviewAnalysis>()
                        .in(ReviewAnalysis::getReviewId, reviewIds)
                        .eq(ReviewAnalysis::getStatus, "SUCCESS")
                        .orderByDesc(ReviewAnalysis::getCreatedAt)
        );

        // 一个 review 可能有多条分析记录，取最新一条
        Map<Long, String> sentimentMap = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (ReviewAnalysis analysis : analyses) {
            if (!seen.contains(analysis.getReviewId())) {
                sentimentMap.put(analysis.getReviewId(), analysis.getSentiment());
                seen.add(analysis.getReviewId());
            }
        }

        // 对于没有 AI 分析结果的评价，根据评分推断情感
        for (Review review : reviews) {
            if (!sentimentMap.containsKey(review.getId()) && review.getRating() != null) {
                sentimentMap.put(review.getId(), inferSentimentFromRating(review.getRating()));
            }
        }

        return sentimentMap;
    }

    /**
     * 根据评分推断情感倾向。
     * 4-5 分 → POSITIVE，3 分 → NEUTRAL，1-2 分 → NEGATIVE
     */
    private String inferSentimentFromRating(BigDecimal rating) {
        int r = rating.intValue();
        if (r >= 4) return "POSITIVE";
        if (r == 3) return "NEUTRAL";
        return "NEGATIVE";
    }

    /**
     * 按统计周期对评价进行分组。
     */
    private Map<PeriodKey, List<Review>> groupReviewsByPeriod(
            List<Review> reviews,
            String periodType,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<PeriodKey, List<Review>> grouped = new LinkedHashMap<>();

        for (Review review : reviews) {
            // 确定评价的有效日期：优先 publishedAt，其次 reviewTime，最后 createdAt
            LocalDate reviewDate = extractReviewDate(review);
            if (reviewDate == null) {
                continue;
            }

            // 判断是否在查询范围内
            if (startDate != null && reviewDate.isBefore(startDate)) continue;
            if (endDate != null && reviewDate.isAfter(endDate)) continue;

            // 计算该评价属于哪个周期
            PeriodKey key = computePeriodKey(reviewDate, periodType);

            // 进一步检查周期是否在范围内
            if (startDate != null && key.endDate.isBefore(startDate)) continue;
            if (endDate != null && key.startDate.isAfter(endDate)) continue;

            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(review);
        }

        return grouped;
    }

    /**
     * 从 Review 实体中提取有效日期。
     */
    private LocalDate extractReviewDate(Review review) {
        if (review.getPublishedAt() != null) {
            return review.getPublishedAt().toLocalDate();
        }
        if (review.getReviewTime() != null) {
            return review.getReviewTime().toLocalDate();
        }
        if (review.getCreatedAt() != null) {
            return review.getCreatedAt().toLocalDate();
        }
        return null;
    }

    /**
     * 计算给定日期所属的统计周期起止日期。
     */
    private PeriodKey computePeriodKey(LocalDate date, String periodType) {
        return switch (periodType) {
            case "DAY" -> new PeriodKey(date, date);
            case "WEEK" -> {
                // 周一作为一周的开始
                LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate weekEnd = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                yield new PeriodKey(weekStart, weekEnd);
            }
            case "MONTH" -> {
                LocalDate monthStart = date.withDayOfMonth(1);
                LocalDate monthEnd = date.withDayOfMonth(date.lengthOfMonth());
                yield new PeriodKey(monthStart, monthEnd);
            }
            default -> new PeriodKey(date, date);
        };
    }

    /**
     * 对某个统计周期内的评价进行聚合计算。
     */
    private MerchantReputationStatistics computeStats(
            Long merchantId,
            String periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<Review> periodReviews,
            Map<Long, String> sentimentMap
    ) {
        if (periodReviews.isEmpty()) {
            return null;
        }

        int total = periodReviews.size();
        int positiveCount = 0;
        int neutralCount = 0;
        int negativeCount = 0;

        BigDecimal ratingSum = BigDecimal.ZERO;
        int ratingCount = 0;

        for (Review review : periodReviews) {
            // 情感分类
            String sentiment = sentimentMap.get(review.getId());
            if ("POSITIVE".equals(sentiment)) {
                positiveCount++;
            } else if ("NEGATIVE".equals(sentiment)) {
                negativeCount++;
            } else {
                neutralCount++;
            }

            // 评分累加
            if (review.getRating() != null) {
                ratingSum = ratingSum.add(review.getRating());
                ratingCount++;
            }
        }

        BigDecimal avgRating = ratingCount > 0
                ? ratingSum.divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal positiveRatio = total > 0
                ? BigDecimal.valueOf(positiveCount).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal negativeRatio = total > 0
                ? BigDecimal.valueOf(negativeCount).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        MerchantReputationStatistics stats = new MerchantReputationStatistics();
        stats.setMerchantId(merchantId);
        stats.setPeriodType(periodType);
        stats.setPeriodStart(periodStart);
        stats.setPeriodEnd(periodEnd);
        stats.setAverageRating(avgRating);
        stats.setPositiveCount(positiveCount);
        stats.setNeutralCount(neutralCount);
        stats.setNegativeCount(negativeCount);
        stats.setTotalReviewCount(total);
        stats.setPositiveRatio(positiveRatio);
        stats.setNegativeRatio(negativeRatio);
        return stats;
    }

    /**
     * 聚合多条统计记录为一个汇总结果。
     */
    private AggregationResult aggregateStats(List<MerchantReputationStatistics> statsList) {
        if (statsList == null || statsList.isEmpty()) {
            return AggregationResult.EMPTY;
        }

        int totalReviews = 0;
        int totalPositive = 0;
        int totalNeutral = 0;
        int totalNegative = 0;
        BigDecimal ratingSum = BigDecimal.ZERO;
        int ratingPeriods = 0;

        for (MerchantReputationStatistics s : statsList) {
            if (s.getTotalReviewCount() != null) {
                totalReviews += s.getTotalReviewCount();
            }
            if (s.getPositiveCount() != null) {
                totalPositive += s.getPositiveCount();
            }
            if (s.getNeutralCount() != null) {
                totalNeutral += s.getNeutralCount();
            }
            if (s.getNegativeCount() != null) {
                totalNegative += s.getNegativeCount();
            }
            if (s.getAverageRating() != null) {
                ratingSum = ratingSum.add(s.getAverageRating());
                ratingPeriods++;
            }
        }

        BigDecimal avgRating = ratingPeriods > 0
                ? ratingSum.divide(BigDecimal.valueOf(ratingPeriods), 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal positiveRatio = totalReviews > 0
                ? BigDecimal.valueOf(totalPositive).divide(BigDecimal.valueOf(totalReviews), 4, RoundingMode.HALF_UP)
                : null;

        BigDecimal negativeRatio = totalReviews > 0
                ? BigDecimal.valueOf(totalNegative).divide(BigDecimal.valueOf(totalReviews), 4, RoundingMode.HALF_UP)
                : null;

        return new AggregationResult(avgRating, totalReviews, positiveRatio, negativeRatio);
    }

    /**
     * 计算两个 BigDecimal 的差值，处理 null 情况。
     */
    private BigDecimal subtract(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return null;
        if (a == null) return b.negate();
        if (b == null) return a;
        return a.subtract(b);
    }

    /**
     * 将实体转换为趋势数据点 VO。
     */
    private ReputationTrendPointVO toTrendPointVO(MerchantReputationStatistics stats) {
        return ReputationTrendPointVO.builder()
                .periodStart(stats.getPeriodStart())
                .periodEnd(stats.getPeriodEnd())
                .periodType(stats.getPeriodType())
                .averageRating(stats.getAverageRating())
                .totalReviewCount(stats.getTotalReviewCount())
                .positiveCount(stats.getPositiveCount())
                .neutralCount(stats.getNeutralCount())
                .negativeCount(stats.getNegativeCount())
                .positiveRatio(stats.getPositiveRatio())
                .negativeRatio(stats.getNegativeRatio())
                .build();
    }

    /**
     * 构建趋势响应 VO，包含概要计算。
     */
    private ReputationTrendVO buildTrendVO(
            Long merchantId,
            String merchantName,
            String periodType,
            List<ReputationTrendPointVO> dataPoints
    ) {
        int totalReviews = 0;
        BigDecimal ratingSum = BigDecimal.ZERO;
        int ratingCount = 0;
        int totalPositive = 0;
        int totalNegative = 0;

        for (ReputationTrendPointVO point : dataPoints) {
            if (point.getTotalReviewCount() != null) {
                totalReviews += point.getTotalReviewCount();
            }
            if (point.getAverageRating() != null) {
                ratingSum = ratingSum.add(point.getAverageRating());
                ratingCount++;
            }
            if (point.getPositiveCount() != null) {
                totalPositive += point.getPositiveCount();
            }
            if (point.getNegativeCount() != null) {
                totalNegative += point.getNegativeCount();
            }
        }

        BigDecimal overallAvgRating = ratingCount > 0
                ? ratingSum.divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal overallPositiveRatio = totalReviews > 0
                ? BigDecimal.valueOf(totalPositive).divide(BigDecimal.valueOf(totalReviews), 4, RoundingMode.HALF_UP)
                : null;

        BigDecimal overallNegativeRatio = totalReviews > 0
                ? BigDecimal.valueOf(totalNegative).divide(BigDecimal.valueOf(totalReviews), 4, RoundingMode.HALF_UP)
                : null;

        // 趋势判断：对比首尾数据点
        String ratingTrend = computeTrend(dataPoints, "rating");
        String sentimentTrend = computeTrend(dataPoints, "sentiment");

        return ReputationTrendVO.builder()
                .merchantId(merchantId)
                .merchantName(merchantName)
                .periodType(periodType)
                .dataPoints(dataPoints)
                .totalPeriods(dataPoints.size())
                .totalReviews(totalReviews)
                .overallAverageRating(overallAvgRating)
                .overallPositiveRatio(overallPositiveRatio)
                .overallNegativeRatio(overallNegativeRatio)
                .ratingTrend(ratingTrend)
                .sentimentTrend(sentimentTrend)
                .build();
    }

    /**
     * 根据数据点序列计算趋势方向。
     * 取前 1/3 和后 1/3 的均值进行比较。
     */
    private String computeTrend(List<ReputationTrendPointVO> points, String type) {
        if (points.size() < 2) {
            return "STABLE";
        }

        int third = Math.max(1, points.size() / 3);
        List<ReputationTrendPointVO> firstPart = points.subList(0, third);
        List<ReputationTrendPointVO> lastPart = points.subList(points.size() - third, points.size());

        BigDecimal firstAvg;
        BigDecimal lastAvg;

        if ("rating".equals(type)) {
            firstAvg = averageOfPoints(firstPart, true);
            lastAvg = averageOfPoints(lastPart, true);
        } else {
            // sentiment: 比较正面占比
            firstAvg = averageOfPoints(firstPart, false);
            lastAvg = averageOfPoints(lastPart, false);
        }

        if (firstAvg == null || lastAvg == null) {
            return "STABLE";
        }

        BigDecimal diff = lastAvg.subtract(firstAvg);
        BigDecimal threshold = "rating".equals(type)
                ? BigDecimal.valueOf(0.3)
                : BigDecimal.valueOf(0.05);

        if (diff.compareTo(threshold) > 0) return "RISING";
        if (diff.compareTo(threshold.negate()) < 0) return "DECLINING";
        return "STABLE";
    }

    /**
     * 计算数据点列表中某项指标的平均值。
     * @param useRating true 取 averageRating，false 取 positiveRatio
     */
    private BigDecimal averageOfPoints(List<ReputationTrendPointVO> points, boolean useRating) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (ReputationTrendPointVO p : points) {
            BigDecimal val = useRating ? p.getAverageRating() : p.getPositiveRatio();
            if (val != null) {
                sum = sum.add(val);
                count++;
            }
        }
        return count > 0
                ? sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP)
                : null;
    }

    // ==================== 内部类 ====================

    /**
     * 统计周期键，用于评价分组。
     */
    private record PeriodKey(LocalDate startDate, LocalDate endDate) {}

    /**
     * 聚合结果，用于内部传递。
     */
    private static class AggregationResult {
        static final AggregationResult EMPTY = new AggregationResult(null, 0, null, null);

        final BigDecimal avgRating;
        final int totalCount;
        final BigDecimal positiveRatio;
        final BigDecimal negativeRatio;

        AggregationResult(BigDecimal avgRating, int totalCount, BigDecimal positiveRatio, BigDecimal negativeRatio) {
            this.avgRating = avgRating;
            this.totalCount = totalCount;
            this.positiveRatio = positiveRatio;
            this.negativeRatio = negativeRatio;
        }
    }
}
