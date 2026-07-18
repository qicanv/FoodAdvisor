package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodadvisor.dto.RegionHotWordVO;
import com.foodadvisor.dto.RegionHotWordVO.HotWordMerchantBrief;
import com.foodadvisor.dto.RegionHotWordVO.RegionBriefVO;
import com.foodadvisor.entity.RegionHotWord;
import com.foodadvisor.mapper.RegionHotWordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 区域热词服务
 *
 * 负责热词的生成、查询和生命周期管理。
 *
 * <h3>热词生成流程</h3>
 * <ol>
 *   <li>从 review_tag_relations + review_tags 聚合 AI 标签数据（基于 tag 的 mention）</li>
 *   <li>从 review_analysis 聚合关键词数据（基于 keywords JSONB）</li>
 *   <li>合并两路数据，按 region_code 分组统计</li>
 *   <li>计算热度分数 = 提及频次权重 × 时间衰减因子 × 情感权重</li>
 *   <li>过滤停用词、违规词、无意义词</li>
 *   <li>写入 region_hot_words 表（旧批次标记为 OUTDATED）</li>
 * </ol>
 *
 * <h3>热度计算公式</h3>
 * <pre>
 *   heatScore = min(mentionCount × recencyFactor × sentimentMultiplier, 100)
 *
 *   recencyFactor = 1.0 / (1 + daysFromNow / decayDays)
 *     - 越近的评价权重越高
 *
 *   sentimentMultiplier:
 *     - POSITIVE: 1.2  (正面评价传播价值更高)
 *     - NEUTRAL:  1.0
 *     - NEGATIVE: 0.8  (负面评价适当降权但不完全排除)
 * </pre>
 */
@Service
public class RegionHotWordService extends ServiceImpl<RegionHotWordMapper, RegionHotWord> {

    private static final Logger log = LoggerFactory.getLogger(RegionHotWordService.class);

    /** 热度上限 */
    private static final BigDecimal MAX_HEAT_SCORE = new BigDecimal("100.00");

    /** 时间衰减半衰期（天） */
    private static final int DECAY_DAYS = 7;

    /** 情感权重 */
    private static final BigDecimal POSITIVE_WEIGHT = new BigDecimal("1.2");
    private static final BigDecimal NEUTRAL_WEIGHT = new BigDecimal("1.0");
    private static final BigDecimal NEGATIVE_WEIGHT = new BigDecimal("0.8");

    /**
     * 预设停用词列表 —— 这些词太通用，没有区分度，不展示为热词。
     * 实际生产环境可从数据库表或配置中心加载。
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "好吃", "不错", "可以", "还行", "一般", "很好", "非常好",
            "喜欢", "推荐", "值得", "不错哦", "不错啊", "还不错",
            "有", "是", "的", "了", "在", "我", "我们", "他", "她",
            "这", "那", "很", "都", "也", "就", "要", "会", "能",
            "去", "来", "到", "和", "与", "但", "让", "把", "被",
            "不", "没", "人", "个", "里", "大", "小", "多", "少"
    );

    private final JdbcTemplate jdbcTemplate;
    private final RegionHotWordMapper regionHotWordMapper;

    public RegionHotWordService(JdbcTemplate jdbcTemplate,
                                RegionHotWordMapper regionHotWordMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.regionHotWordMapper = regionHotWordMapper;
    }

    // ==================== 热词查询 ====================

    /**
     * 分页查询指定区域的当前热词（按热度降序）。
     *
     * @param regionCode 区域编码，如 "310100"；传 null 表示查询所有区域
     * @param category   分类筛选，如 "TASTE"；传 null 表示不筛选
     * @param periodType 周期类型：DAILY / WEEKLY / MONTHLY；默认 WEEKLY
     * @param pageNum    页码，从 1 开始
     * @param pageSize   每页条数，默认 20
     * @return 分页热词结果
     */
    public Page<RegionHotWordVO> queryHotWords(String regionCode, String category,
                                                String periodType, int pageNum, int pageSize) {
        LambdaQueryWrapper<RegionHotWord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegionHotWord::getStatus, "ACTIVE");

        if (regionCode != null && !regionCode.isBlank()) {
            wrapper.eq(RegionHotWord::getRegionCode, regionCode);
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(RegionHotWord::getCategory, category);
        }
        if (periodType != null && !periodType.isBlank()) {
            wrapper.eq(RegionHotWord::getPeriodType, periodType);
        } else {
            wrapper.eq(RegionHotWord::getPeriodType, "WEEKLY");
        }

        wrapper.orderByDesc(RegionHotWord::getHeatScore);

        Page<RegionHotWord> entityPage = Page.of(pageNum, pageSize);
        regionHotWordMapper.selectPage(entityPage, wrapper);

        // 转换为 VO
        Page<RegionHotWordVO> voPage = new Page<>(pageNum, pageSize, entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 查询所有有热词数据的区域列表（供前端区域选择器使用）。
     *
     * @return 区域简要信息列表
     */
    public List<RegionBriefVO> listRegionsWithHotWords() {
        String sql = """
                SELECT
                    r.region_code,
                    COUNT(*) AS hot_word_count,
                    (SELECT r2.word FROM region_hot_words r2
                     WHERE r2.region_code = r.region_code
                       AND r2.status = 'ACTIVE'
                     ORDER BY r2.heat_score DESC
                     LIMIT 1) AS top_word
                FROM region_hot_words r
                WHERE r.status = 'ACTIVE'
                GROUP BY r.region_code
                ORDER BY hot_word_count DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> RegionBriefVO.builder()
                .regionCode(rs.getString("region_code"))
                .hotWordCount(rs.getInt("hot_word_count"))
                .topWord(rs.getString("top_word"))
                .build());
    }

    /**
     * 查询某热词关联的商家列表（点击热词查看关联商家）。
     *
     * @param hotWordId 热词 ID
     * @param limit     最多返回的商家数
     * @return 商家简要信息列表
     */
    public List<HotWordMerchantBrief> getAssociatedMerchants(Long hotWordId, int limit) {
        RegionHotWord hotWord = regionHotWordMapper.selectById(hotWordId);
        if (hotWord == null) {
            return Collections.emptyList();
        }

        // 从 review_tag_relations -> reviews -> merchants 查询该热词关联的商家
        String sql = """
                SELECT
                    m.id AS merchant_id,
                    m.name AS merchant_name,
                    m.category AS merchant_category,
                    COUNT(DISTINCT rtr.review_id) AS mention_count
                FROM review_tag_relations rtr
                JOIN review_tags rt ON rtr.tag_id = rt.id
                JOIN reviews rv ON rtr.review_id = rv.id
                    AND rtr.review_version <= rv.current_version
                    AND rv.status = 'PUBLISHED'
                JOIN merchants m ON rv.merchant_id = m.id
                WHERE rt.name = ?
                  AND m.region_code = ?
                  AND rv.created_at >= ?
                GROUP BY m.id, m.name, m.category
                ORDER BY mention_count DESC
                LIMIT ?
                """;

        LocalDate periodStart = hotWord.getPeriodStart();
        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, hotWord.getWord());
                    ps.setString(2, hotWord.getRegionCode());
                    ps.setObject(3, periodStart);
                    ps.setInt(4, limit);
                },
                (rs, rowNum) -> HotWordMerchantBrief.builder()
                        .merchantId(rs.getLong("merchant_id"))
                        .merchantName(rs.getString("merchant_name"))
                        .category(rs.getString("merchant_category"))
                        .mentionCount(rs.getInt("mention_count"))
                        .build());
    }

    // ==================== 热词生成 ====================

    /**
     * 为所有区域重新生成热词。
     *
     * 该方法可由定时任务自动触发，也可由管理员手动调用。
     * 生成完成后，旧批次的热词会被标记为 OUTDATED。
     *
     * @param periodType 周期类型：DAILY / WEEKLY / MONTHLY
     * @param daysBack   回溯天数（从今天往前推 daysBack 天作为统计窗口）
     * @return 生成的热词总数
     */
    public int regenerateAll(String periodType, int daysBack) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(daysBack);

        log.info("开始全量热词生成: periodType={}, start={}, end={}", periodType, startDate, endDate);

        // 1. 计算下一个版本号
        int nextVersion = getNextVersion(periodType, startDate, endDate);

        // 2. 将旧版本标记为 OUTDATED
        markOutdated(null, periodType, startDate, endDate, nextVersion);

        // 3. 获取所有有评价的区域
        List<String> allRegions = getAllActiveRegions();
        log.info("共发现 {} 个活跃区域需要生成热词", allRegions.size());

        int totalGenerated = 0;
        for (String regionCode : allRegions) {
            try {
                int count = generateForRegion(regionCode, periodType, startDate, endDate, nextVersion);
                totalGenerated += count;
                log.info("区域 {} 生成 {} 个热词", regionCode, count);
            } catch (Exception e) {
                log.error("区域 {} 热词生成失败: {}", regionCode, e.getMessage(), e);
            }
        }

        log.info("全量热词生成完成: 共生成 {} 个热词（版本 {}）", totalGenerated, nextVersion);
        return totalGenerated;
    }

    /**
     * 为单个区域生成热词。
     *
     * @param regionCode 区域编码
     * @param periodType 周期类型
     * @param startDate  统计起始日期
     * @param endDate    统计截止日期
     * @param version    批次版本号
     * @return 该区域生成的热词数量
     */
    public int generateForRegion(String regionCode, String periodType,
                                  LocalDate startDate, LocalDate endDate, int version) {
        // 1. 聚合 AI 标签数据
        List<WordStat> tagStats = aggregateFromTags(regionCode, startDate, endDate);
        // 2. 聚合关键词数据
        List<WordStat> keywordStats = aggregateFromKeywords(regionCode, startDate, endDate);
        // 3. 合并
        Map<String, WordStat> merged = mergeWordStats(tagStats, keywordStats);
        // 4. 过滤停用词
        merged = filterStopWords(merged);
        // 5. 计算热度并写入数据库
        List<RegionHotWord> hotWords = buildHotWordEntities(
                merged, regionCode, periodType, startDate, endDate, version);

        if (!hotWords.isEmpty()) {
            // 逐条插入，跳过重复键（由唯一约束 uk_region_hot_word_period 保护）
            for (RegionHotWord hw : hotWords) {
                try {
                    regionHotWordMapper.insert(hw);
                } catch (Exception e) {
                    // 重复键或约束违反 → 跳过，不影响其他热词
                    log.debug("插入热词失败(跳过): word={}, error={}", hw.getWord(), e.getMessage());
                }
            }
        }

        return hotWords.size();
    }

    // ==================== 数据聚合 ====================

    /**
     * 从 review_tag_relations 表中聚合标签数据。
     *
     * 关联链路：reviews -> review_tag_relations -> review_tags -> merchants
     * 统计每个标签（tag.name）在指定区域内的出现次数和相关评价/商家数。
     */
    private List<WordStat> aggregateFromTags(String regionCode, LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT
                    rt.name AS word,
                    rt.category AS category,
                    rtr.sentiment,
                    COUNT(*) AS mention_count,
                    COUNT(DISTINCT rtr.review_id) AS review_count,
                    COUNT(DISTINCT rv.merchant_id) AS merchant_count,
                    SUM(CASE WHEN rtr.sentiment = 'POSITIVE' THEN 1 ELSE 0 END)::NUMERIC
                        / NULLIF(COUNT(*), 0) AS positive_ratio
                FROM review_tag_relations rtr
                JOIN review_tags rt ON rtr.tag_id = rt.id AND rt.status = 'ACTIVE'
                JOIN reviews rv ON rtr.review_id = rv.id
                    AND rtr.review_version <= rv.current_version
                    AND rv.status = 'PUBLISHED'
                JOIN merchants m ON rv.merchant_id = m.id
                    AND m.platform_status = 'ACTIVE'
                    AND m.operation_status = 'OPEN'
                WHERE m.region_code = ?
                  AND rv.created_at >= ?
                  AND rv.created_at <  (?::DATE + INTERVAL '1 day')
                GROUP BY rt.name, rt.category, rtr.sentiment
                HAVING COUNT(*) > 0
                ORDER BY mention_count DESC
                """;

        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, regionCode);
                    ps.setObject(2, startDate);
                    ps.setObject(3, endDate);
                },
                (rs, rowNum) -> {
                    WordStat stat = new WordStat();
                    stat.word = rs.getString("word");
                    stat.category = mapCategory(rs.getString("category"));
                    stat.sentiment = rs.getString("sentiment");
                    stat.mentionCount = rs.getInt("mention_count");
                    stat.reviewCount = rs.getInt("review_count");
                    stat.merchantCount = rs.getInt("merchant_count");
                    stat.positiveRatio = rs.getBigDecimal("positive_ratio");
                    stat.sourceType = "AI_TAG";
                    return stat;
                });
    }

    /**
     * 从 review_analysis 表的 keywords JSONB 字段聚合关键词。
     *
     * review_analysis.keywords 存储的是 AI 提取的关键词 JSON 数组，如 ["麻辣", "上菜慢", ...]
     * 这路数据与标签路数据互补 —— 有些关键词可能没有对应的标签定义。
     */
    private List<WordStat> aggregateFromKeywords(String regionCode, LocalDate startDate, LocalDate endDate) {
        // PostgreSQL JSONB 聚合：展开 keywords 数组，每个关键词一行，然后统计
        String sql = """
                WITH keyword_rows AS (
                    SELECT
                        jsonb_array_elements_text(ra.keywords) AS word,
                        ra.sentiment,
                        rv.merchant_id,
                        rv.id AS review_id
                    FROM review_analysis ra
                    JOIN reviews rv ON ra.review_id = rv.id
                        AND ra.review_version <= rv.current_version
                        AND rv.status = 'PUBLISHED'
                    JOIN merchants m ON rv.merchant_id = m.id
                        AND m.platform_status = 'ACTIVE'
                        AND m.operation_status = 'OPEN'
                    WHERE m.region_code = ?
                      AND ra.status = 'SUCCESS'
                      AND ra.keywords IS NOT NULL
                      AND jsonb_array_length(ra.keywords) > 0
                      AND rv.created_at >= ?
                      AND rv.created_at <  (?::DATE + INTERVAL '1 day')
                )
                SELECT
                    word,
                    sentiment,
                    COUNT(*) AS mention_count,
                    COUNT(DISTINCT review_id) AS review_count,
                    COUNT(DISTINCT merchant_id) AS merchant_count,
                    SUM(CASE WHEN sentiment = 'POSITIVE' THEN 1 ELSE 0 END)::NUMERIC
                        / NULLIF(COUNT(*), 0) AS positive_ratio
                FROM keyword_rows
                WHERE word IS NOT NULL AND trim(word) <> ''
                GROUP BY word, sentiment
                HAVING COUNT(*) > 0
                ORDER BY mention_count DESC
                """;

        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, regionCode);
                    ps.setObject(2, startDate);
                    ps.setObject(3, endDate);
                },
                (rs, rowNum) -> {
                    WordStat stat = new WordStat();
                    stat.word = rs.getString("word");
                    stat.category = "GENERAL";        // 关键词提取没有明确的分类
                    stat.sentiment = rs.getString("sentiment");
                    stat.mentionCount = rs.getInt("mention_count");
                    stat.reviewCount = rs.getInt("review_count");
                    stat.merchantCount = rs.getInt("merchant_count");
                    stat.positiveRatio = rs.getBigDecimal("positive_ratio");
                    stat.sourceType = "KEYWORD_EXTRACT";
                    return stat;
                });
    }

    // ==================== 数据合并与过滤 ====================

    /**
     * 合并两路数据源（AI标签 + 关键词提取）。
     *
     * 合并规则：
     * - 同一 word + sentiment 组合，频次数值相加
     * - sourceType 优先标记为 AI_TAG（如果有的话）
     */
    private Map<String, WordStat> mergeWordStats(List<WordStat> tagStats, List<WordStat> keywordStats) {
        Map<String, WordStat> merged = new LinkedHashMap<>();

        // 先放入标签数据
        for (WordStat stat : tagStats) {
            String key = buildMergeKey(stat.word, stat.sentiment);
            merged.put(key, stat);
        }

        // 合并关键词数据
        for (WordStat kw : keywordStats) {
            String key = buildMergeKey(kw.word, kw.sentiment);
            WordStat existing = merged.get(key);
            if (existing != null) {
                // 合并：频次相加，取最大值
                existing.mentionCount += kw.mentionCount;
                existing.reviewCount = Math.max(existing.reviewCount, kw.reviewCount);
                existing.merchantCount = Math.max(existing.merchantCount, kw.merchantCount);
                // positiveRatio 以标签数据为准（更可靠）
            } else {
                merged.put(key, kw);
            }
        }

        return merged;
    }

    /**
     * 过滤停用词、无意义词和过长/过短的词。
     */
    private Map<String, WordStat> filterStopWords(Map<String, WordStat> stats) {
        return stats.entrySet().stream()
                .filter(entry -> {
                    String word = entry.getValue().word;
                    // 过滤空或空白
                    if (word == null || word.isBlank()) return false;
                    // 过滤停用词
                    if (STOP_WORDS.contains(word.trim())) return false;
                    // 过滤过长或过短的词（1个字太短，超过15个字不像热词）
                    String trimmed = word.trim();
                    if (trimmed.length() < 2 || trimmed.length() > 15) return false;
                    // 过滤纯数字或纯符号
                    if (trimmed.matches("^[\\d.]+$") || trimmed.matches("^[^\\w\\u4e00-\\u9fa5]+$"))
                        return false;
                    return true;
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    // ==================== 热度计算 ====================

    /**
     * 构建热词实体列表，计算热度分数。
     *
     * 热度计算公式：
     * <pre>
     *   rawScore = mentionCount × recencyFactor × sentimentMultiplier
     *   heatScore = min(rawScore, MAX_HEAT_SCORE)
     *
     *   其中 recencyFactor = 1.0 / (1 + N / DECAY_DAYS)
     *     N = 数据平均年龄（天），取 (endDate - createdAt) 的中间值
     * </pre>
     */
    private List<RegionHotWord> buildHotWordEntities(Map<String, WordStat> stats,
                                                      String regionCode, String periodType,
                                                      LocalDate startDate, LocalDate endDate,
                                                      int version) {
        // 时间衰减因子：基于统计窗口的中间日期计算
        long daysFromMidpoint = ChronoUnit.DAYS.between(
                startDate.plusDays((ChronoUnit.DAYS.between(startDate, endDate)) / 2),
                LocalDate.now());
        double recencyFactor = 1.0 / (1.0 + (double) daysFromMidpoint / DECAY_DAYS);

        List<RegionHotWord> hotWords = new ArrayList<>();

        for (WordStat stat : stats.values()) {
            // 获取情感权重
            BigDecimal sentimentWeight = switch (stat.sentiment != null ? stat.sentiment : "NEUTRAL") {
                case "POSITIVE" -> POSITIVE_WEIGHT;
                case "NEGATIVE" -> NEGATIVE_WEIGHT;
                default -> NEUTRAL_WEIGHT;
            };

            // 基础分数 = 提及次数 × 时间衰减 × 情感权重，然后归一化到 0~100
            double rawScore = stat.mentionCount * recencyFactor * sentimentWeight.doubleValue();
            // 归一化：假设单区域热词最大提及次数约 200 次
            double normalized = Math.min(rawScore * 100.0 / 200.0, 100.0);
            BigDecimal heatScore = BigDecimal.valueOf(normalized)
                    .setScale(2, RoundingMode.HALF_UP);
            // 确保在 [0, 100] 范围
            if (heatScore.compareTo(BigDecimal.ZERO) < 0) heatScore = BigDecimal.ZERO;
            if (heatScore.compareTo(MAX_HEAT_SCORE) > 0) heatScore = MAX_HEAT_SCORE;

            RegionHotWord hw = new RegionHotWord();
            hw.setRegionCode(regionCode);
            hw.setWord(stat.word);
            hw.setCategory(stat.category);
            hw.setSentiment(stat.sentiment);
            hw.setHeatScore(heatScore);
            hw.setMentionCount(stat.mentionCount);
            hw.setReviewCount(stat.reviewCount);
            hw.setMerchantCount(stat.merchantCount);
            hw.setPositiveRatio(stat.positiveRatio);
            hw.setSourceType(stat.sourceType);
            hw.setPeriodType(periodType);
            hw.setPeriodStart(startDate);
            hw.setPeriodEnd(endDate);
            hw.setVersion(version);
            hw.setStatus("ACTIVE");

            hotWords.add(hw);
        }

        // 按热度降序排列，最多保留前 100 个
        hotWords.sort((a, b) -> b.getHeatScore().compareTo(a.getHeatScore()));
        if (hotWords.size() > 100) {
            hotWords = hotWords.subList(0, 100);
        }

        return hotWords;
    }

    // ==================== 辅助方法 ====================

    /**
     * 将旧版本的热词标记为 OUTDATED。
     */
    private void markOutdated(String regionCode, String periodType,
                              LocalDate startDate, LocalDate endDate, int currentVersion) {
        String sql = """
                UPDATE region_hot_words
                SET status = 'OUTDATED', updated_at = CURRENT_TIMESTAMP
                WHERE status = 'ACTIVE'
                  AND version < ?
                  AND period_type = ?
                  AND period_start = ?
                  AND period_end = ?
                """;

        List<Object> params = new ArrayList<>();
        params.add(currentVersion);
        params.add(periodType);
        params.add(startDate);
        params.add(endDate);

        if (regionCode != null && !regionCode.isBlank()) {
            sql += " AND region_code = ?";
            params.add(regionCode);
        }

        jdbcTemplate.update(sql, params.toArray());
    }

    /**
     * 获取下一个版本号（公开方法，供 Controller 调用）。
     */
    public int getNextVersionHelper(String periodType, LocalDate startDate, LocalDate endDate) {
        return getNextVersion(periodType, startDate, endDate);
    }

    /**
     * 获取下一个版本号。
     */
    private int getNextVersion(String periodType, LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT COALESCE(MAX(version), 0) + 1
                FROM region_hot_words
                WHERE period_type = ?
                  AND period_start = ?
                  AND period_end = ?
                """;
        Integer next = jdbcTemplate.queryForObject(sql, Integer.class, periodType, startDate, endDate);
        return next != null ? next : 1;
    }

    /**
     * 获取所有有评价数据的活跃区域编码列表。
     */
    private List<String> getAllActiveRegions() {
        String sql = """
                SELECT DISTINCT m.region_code
                FROM merchants m
                JOIN reviews rv ON m.id = rv.merchant_id
                    AND rv.status = 'PUBLISHED'
                WHERE m.platform_status = 'ACTIVE'
                  AND m.operation_status = 'OPEN'
                  AND m.region_code IS NOT NULL
                  AND trim(m.region_code) <> ''
                ORDER BY m.region_code
                """;
        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * 将 review_tags 的 category 映射为热词的 category 枚举。
     */
    private String mapCategory(String tagCategory) {
        if (tagCategory == null) return "GENERAL";
        return switch (tagCategory.toUpperCase()) {
            case "TASTE", "FLAVOR" -> "TASTE";
            case "SERVICE" -> "SERVICE";
            case "ENVIRONMENT", "ATMOSPHERE" -> "ENVIRONMENT";
            case "PRICE", "VALUE" -> "PRICE";
            case "SPEED", "EFFICIENCY" -> "SPEED";
            default -> "GENERAL";
        };
    }

    private String buildMergeKey(String word, String sentiment) {
        return (word != null ? word.trim() : "") + "|" + (sentiment != null ? sentiment : "NEUTRAL");
    }

    /**
     * 实体转 VO。
     */
    private RegionHotWordVO toVO(RegionHotWord entity) {
        return RegionHotWordVO.builder()
                .id(entity.getId())
                .regionCode(entity.getRegionCode())
                .word(entity.getWord())
                .category(entity.getCategory())
                .sentiment(entity.getSentiment())
                .heatScore(entity.getHeatScore())
                .mentionCount(entity.getMentionCount())
                .reviewCount(entity.getReviewCount())
                .merchantCount(entity.getMerchantCount())
                .positiveRatio(entity.getPositiveRatio())
                .periodType(entity.getPeriodType())
                .periodStart(entity.getPeriodStart())
                .periodEnd(entity.getPeriodEnd())
                .build();
    }

    // ==================== 内部统计类 ====================

    /**
     * 热词中间统计数据（生成过程中的临时对象）。
     */
    private static class WordStat {
        String word;
        String category;
        String sentiment;
        int mentionCount;
        int reviewCount;
        int merchantCount;
        BigDecimal positiveRatio;
        String sourceType;
    }
}
