package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodadvisor.dto.RegionHotWordVO;
import com.foodadvisor.dto.RegionHotWordVO.HotWordMerchantBrief;
import com.foodadvisor.dto.RegionHotWordVO.HotWordReviewBrief;
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

        Page<RegionHotWord> entityPage = Page.of(pageNum, pageSize);
        regionHotWordMapper.selectPage(entityPage, wrapper);

        // 转换为 VO，并用实时 UNION 查询修正 mentionCount
        List<RegionHotWordVO> voList = entityPage.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        if (!voList.isEmpty()) {
            Map<Long, Integer> liveCounts = batchLiveMentionCounts(voList);
            for (RegionHotWordVO vo : voList) {
                Integer live = liveCounts.get(vo.getId());
                if (live != null) {
                    vo.setMentionCount(live);
                }
            }
            // 按实时提及次数降序重新排列
            voList.sort((a, b) -> Integer.compare(
                    b.getMentionCount() != null ? b.getMentionCount() : 0,
                    a.getMentionCount() != null ? a.getMentionCount() : 0));
        }

        Page<RegionHotWordVO> voPage = new Page<>(pageNum, pageSize, entityPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 批量计算热词的实时提及次数（去重评价数），与关联商家下钻使用完全相同的 UNION 逻辑。
     */
    private Map<Long, Integer> batchLiveMentionCounts(List<RegionHotWordVO> words) {
        if (words.isEmpty()) return Collections.emptyMap();

        String sql = """
                WITH tag_reviews AS (
                    SELECT
                        hw.id AS hw_id,
                        rv.id AS review_id
                    FROM region_hot_words hw
                    JOIN review_tag_relations rtr ON true
                    JOIN review_tags rt ON rtr.tag_id = rt.id AND rt.status = 'ACTIVE'
                    JOIN reviews rv ON rtr.review_id = rv.id
                        AND rtr.review_version <= rv.current_version
                        AND rv.status = 'PUBLISHED'
                    JOIN merchants m ON rv.merchant_id = m.id
                        AND m.platform_status = 'ACTIVE'
                        AND m.operation_status = 'OPERATING'
                    WHERE rt.name = hw.word
                      AND m.region_code = hw.region_code
                      AND rv.created_at >= hw.period_start
                      AND rv.created_at <  (hw.period_end + INTERVAL '1 day')
                      AND hw.id = ANY(?)
                ),
                kw_reviews AS (
                    SELECT
                        hw.id AS hw_id,
                        rv.id AS review_id
                    FROM region_hot_words hw
                    JOIN review_analysis ra ON true
                    JOIN reviews rv ON ra.review_id = rv.id
                        AND ra.review_version <= rv.current_version
                        AND rv.status = 'PUBLISHED'
                    JOIN merchants m ON rv.merchant_id = m.id
                        AND m.platform_status = 'ACTIVE'
                        AND m.operation_status = 'OPERATING'
                    CROSS JOIN LATERAL jsonb_array_elements_text(ra.keywords) AS jt(word)
                    WHERE jt.word = hw.word
                      AND ra.status = 'SUCCESS'
                      AND ra.keywords IS NOT NULL
                      AND jsonb_array_length(ra.keywords) > 0
                      AND m.region_code = hw.region_code
                      AND rv.created_at >= hw.period_start
                      AND rv.created_at <  (hw.period_end + INTERVAL '1 day')
                      AND hw.id = ANY(?)
                ),
                combined AS (
                    SELECT hw_id, review_id FROM tag_reviews
                    UNION
                    SELECT hw_id, review_id FROM kw_reviews
                )
                SELECT hw_id, COUNT(DISTINCT review_id) AS cnt
                FROM combined
                GROUP BY hw_id
                """;

        Long[] ids = words.stream().map(RegionHotWordVO::getId).toArray(Long[]::new);
        Map<Long, Integer> result = new LinkedHashMap<>();

        jdbcTemplate.query(sql,
                ps -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids));
                    ps.setArray(2, ps.getConnection().createArrayOf("bigint", ids));
                },
                (rs) -> {
                    result.put(rs.getLong("hw_id"), rs.getInt("cnt"));
                });

        return result;
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
     * 查询某热词关联的商家列表（第一层下钻）。
     *
     * 同时从标签和关键词两个数据源查询，按商家聚合提及次数。
     * 条件与 aggregateFromTags / aggregateFromKeywords 完全一致。
     *
     * @param hotWordId 热词 ID
     * @param limit     最多返回的商家数
     * @return 商家及其提及次数列表
     */
    public List<HotWordMerchantBrief> getAssociatedMerchants(Long hotWordId, int limit) {
        RegionHotWord hotWord = regionHotWordMapper.selectById(hotWordId);
        if (hotWord == null) {
            return Collections.emptyList();
        }

        LocalDate periodStart = hotWord.getPeriodStart();
        LocalDate periodEnd = hotWord.getPeriodEnd();
        String word = hotWord.getWord();
        String regionCode = hotWord.getRegionCode();

        String sql = """
                WITH tag_data AS (
                    SELECT rv.merchant_id, rv.id AS review_id
                    FROM review_tag_relations rtr
                    JOIN review_tags rt ON rtr.tag_id = rt.id AND rt.status = 'ACTIVE'
                    JOIN reviews rv ON rtr.review_id = rv.id
                        AND rtr.review_version <= rv.current_version
                        AND rv.status = 'PUBLISHED'
                    JOIN merchants m ON rv.merchant_id = m.id
                        AND m.platform_status = 'ACTIVE'
                        AND m.operation_status = 'OPERATING'
                    WHERE rt.name = ?
                      AND m.region_code = ?
                      AND rv.created_at >= ?
                      AND rv.created_at <  (?::DATE + INTERVAL '1 day')
                ),
                kw_data AS (
                    SELECT rv.merchant_id, rv.id AS review_id
                    FROM review_analysis ra
                    JOIN reviews rv ON ra.review_id = rv.id
                        AND ra.review_version <= rv.current_version
                        AND rv.status = 'PUBLISHED'
                    JOIN merchants m ON rv.merchant_id = m.id
                        AND m.platform_status = 'ACTIVE'
                        AND m.operation_status = 'OPERATING'
                    WHERE ra.status = 'SUCCESS'
                      AND ra.keywords IS NOT NULL
                      AND jsonb_array_length(ra.keywords) > 0
                      AND ?::text IN (SELECT jsonb_array_elements_text(ra.keywords))
                      AND m.region_code = ?
                      AND rv.created_at >= ?
                      AND rv.created_at <  (?::DATE + INTERVAL '1 day')
                ),
                merged AS (
                    SELECT merchant_id, review_id FROM tag_data
                    UNION
                    SELECT merchant_id, review_id FROM kw_data
                )
                SELECT
                    m.id AS merchant_id,
                    m.name AS merchant_name,
                    m.category AS merchant_category,
                    COUNT(DISTINCT merged.review_id) AS mention_count
                FROM merged
                JOIN merchants m ON merged.merchant_id = m.id
                GROUP BY m.id, m.name, m.category
                ORDER BY mention_count DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, word);       // tag: rt.name
                    ps.setString(2, regionCode);
                    ps.setObject(3, periodStart);
                    ps.setObject(4, periodEnd);
                    ps.setString(5, word);       // kw: keyword match
                    ps.setString(6, regionCode);
                    ps.setObject(7, periodStart);
                    ps.setObject(8, periodEnd);
                    ps.setInt(9, limit);
                },
                (rs, rowNum) -> HotWordMerchantBrief.builder()
                        .merchantId(rs.getLong("merchant_id"))
                        .merchantName(rs.getString("merchant_name"))
                        .category(rs.getString("merchant_category"))
                        .mentionCount(rs.getInt("mention_count"))
                        .build());
    }

    /**
     * 查询某热词在某商家下的具体评价（第二层下钻）。
     *
     * @param hotWordId  热词 ID
     * @param merchantId 商家 ID
     * @param limit      最多返回的评价数
     * @return 评价列表
     */
    public List<HotWordReviewBrief> getMerchantReviews(Long hotWordId, Long merchantId, int limit) {
        RegionHotWord hotWord = regionHotWordMapper.selectById(hotWordId);
        if (hotWord == null) {
            return Collections.emptyList();
        }

        LocalDate periodStart = hotWord.getPeriodStart();
        LocalDate periodEnd = hotWord.getPeriodEnd();
        String word = hotWord.getWord();
        String regionCode = hotWord.getRegionCode();

        // 不在 UNION 中包含 source_type，避免同一评价因来源不同而出现两次
        String sql = """
                WITH tag_reviews AS (
                    SELECT
                        rv.id AS review_id,
                        LEFT(rv.content, 200) AS content,
                        rv.rating,
                        TO_CHAR(rv.review_time, 'YYYY-MM-DD HH24:MI') AS review_time,
                        m.id AS merchant_id,
                        m.name AS merchant_name
                    FROM review_tag_relations rtr
                    JOIN review_tags rt ON rtr.tag_id = rt.id AND rt.status = 'ACTIVE'
                    JOIN reviews rv ON rtr.review_id = rv.id
                        AND rtr.review_version <= rv.current_version
                        AND rv.status = 'PUBLISHED'
                    JOIN merchants m ON rv.merchant_id = m.id
                        AND m.platform_status = 'ACTIVE'
                        AND m.operation_status = 'OPERATING'
                    WHERE rt.name = ?
                      AND m.region_code = ?
                      AND rv.merchant_id = ?
                      AND rv.created_at >= ?
                      AND rv.created_at <  (?::DATE + INTERVAL '1 day')
                ),
                kw_reviews AS (
                    SELECT
                        rv.id AS review_id,
                        LEFT(rv.content, 200) AS content,
                        rv.rating,
                        TO_CHAR(rv.review_time, 'YYYY-MM-DD HH24:MI') AS review_time,
                        m.id AS merchant_id,
                        m.name AS merchant_name
                    FROM review_analysis ra
                    JOIN reviews rv ON ra.review_id = rv.id
                        AND ra.review_version <= rv.current_version
                        AND rv.status = 'PUBLISHED'
                    JOIN merchants m ON rv.merchant_id = m.id
                        AND m.platform_status = 'ACTIVE'
                        AND m.operation_status = 'OPERATING'
                    WHERE ra.status = 'SUCCESS'
                      AND ra.keywords IS NOT NULL
                      AND jsonb_array_length(ra.keywords) > 0
                      AND ?::text IN (SELECT jsonb_array_elements_text(ra.keywords))
                      AND m.region_code = ?
                      AND rv.merchant_id = ?
                      AND rv.created_at >= ?
                      AND rv.created_at <  (?::DATE + INTERVAL '1 day')
                )
                SELECT * FROM tag_reviews
                UNION
                SELECT * FROM kw_reviews
                ORDER BY review_time DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, word);       // tag: rt.name
                    ps.setString(2, regionCode);
                    ps.setLong(3, merchantId);
                    ps.setObject(4, periodStart);
                    ps.setObject(5, periodEnd);
                    ps.setString(6, word);       // kw: keyword match
                    ps.setString(7, regionCode);
                    ps.setLong(8, merchantId);
                    ps.setObject(9, periodStart);
                    ps.setObject(10, periodEnd);
                    ps.setInt(11, limit);
                },
                (rs, rowNum) -> HotWordReviewBrief.builder()
                        .reviewId(rs.getLong("review_id"))
                        .content(rs.getString("content"))
                        .rating(rs.getInt("rating"))
                        .reviewTime(rs.getString("review_time"))
                        .merchantId(rs.getLong("merchant_id"))
                        .merchantName(rs.getString("merchant_name"))
                        .sourceType(null)
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
        // 一条 SQL 完成标签+关键词的 UNION 聚合，保证所有计数（提及次数、商家数）
        // 与下钻查询完全一致，不再需要 Java 侧合并
        String sql = """
                WITH tag_rows AS (
                    SELECT
                        rt.name AS word,
                        rt.category AS tag_category,
                        rtr.sentiment,
                        rv.id AS review_id,
                        rv.merchant_id
                    FROM review_tag_relations rtr
                    JOIN review_tags rt ON rtr.tag_id = rt.id AND rt.status = 'ACTIVE'
                    JOIN reviews rv ON rtr.review_id = rv.id
                        AND rtr.review_version <= rv.current_version
                        AND rv.status = 'PUBLISHED'
                    JOIN merchants m ON rv.merchant_id = m.id
                        AND m.platform_status = 'ACTIVE'
                        AND m.operation_status = 'OPERATING'
                    WHERE m.region_code = ?
                      AND rv.created_at >= ?
                      AND rv.created_at <  (?::DATE + INTERVAL '1 day')
                ),
                kw_rows AS (
                    SELECT
                        jt.word,
                        COALESCE(
                            rt_exact.category,
                            rt_sub.category,
                            'GENERAL'
                        ) AS tag_category,
                        ra.sentiment,
                        rv.id AS review_id,
                        rv.merchant_id
                    FROM review_analysis ra
                    JOIN reviews rv ON ra.review_id = rv.id
                        AND ra.review_version <= rv.current_version
                        AND rv.status = 'PUBLISHED'
                    JOIN merchants m ON rv.merchant_id = m.id
                        AND m.platform_status = 'ACTIVE'
                        AND m.operation_status = 'OPERATING'
                    CROSS JOIN LATERAL jsonb_array_elements_text(ra.keywords) AS jt(word)
                    LEFT JOIN review_tags rt_exact
                        ON rt_exact.name = jt.word AND rt_exact.status = 'ACTIVE'
                    LEFT JOIN LATERAL (
                        SELECT rt2.category FROM review_tags rt2
                        WHERE rt2.status = 'ACTIVE'
                          AND rt_exact.name IS NULL
                          AND jt.word LIKE '%' || rt2.name || '%'
                        ORDER BY char_length(rt2.name) DESC
                        LIMIT 1
                    ) rt_sub ON true
                    WHERE ra.status = 'SUCCESS'
                      AND ra.keywords IS NOT NULL
                      AND jsonb_array_length(ra.keywords) > 0
                      AND m.region_code = ?
                      AND rv.created_at >= ?
                      AND rv.created_at <  (?::DATE + INTERVAL '1 day')
                ),
                all_rows AS (
                    SELECT word, tag_category, sentiment, review_id, merchant_id, 'TAG' AS src FROM tag_rows
                    UNION
                    SELECT word, tag_category, sentiment, review_id, merchant_id, 'KW'  AS src FROM kw_rows
                )
                SELECT
                    word,
                    COALESCE(
                        MAX(CASE WHEN tag_category <> 'GENERAL' THEN tag_category END),
                        'GENERAL'
                    ) AS category,
                    sentiment,
                    COUNT(DISTINCT review_id)                 AS mention_count,
                    COUNT(DISTINCT review_id)                 AS review_count,
                    COUNT(DISTINCT merchant_id)               AS merchant_count,
                    CASE WHEN sentiment = 'POSITIVE' THEN 1.0000
                         WHEN sentiment = 'NEGATIVE' THEN 0.0000
                         ELSE NULL END                       AS positive_ratio,
                    COALESCE(
                        CASE WHEN MAX(CASE WHEN src = 'TAG' THEN 1 ELSE 0 END) > 0
                             THEN 'AI_TAG' ELSE 'KEYWORD_EXTRACT' END,
                        'AI_TAG'
                    ) AS source_type
                FROM all_rows
                WHERE word IS NOT NULL AND trim(word) <> ''
                GROUP BY word, sentiment
                HAVING COUNT(DISTINCT review_id) > 0
                ORDER BY mention_count DESC
                """;

        // 加载标签名称集合，用于后续合并
        Set<String> tagNames = loadTagNames();

        List<WordStat> stats = jdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, regionCode);  // tag: region
                    ps.setObject(2, startDate);   // tag: start
                    ps.setObject(3, endDate);     // tag: end
                    ps.setString(4, regionCode);  // kw: region
                    ps.setObject(5, startDate);   // kw: start
                    ps.setObject(6, endDate);     // kw: end
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
                    stat.sourceType = rs.getString("source_type");
                    return stat;
                });

        // ===== 合并复合关键词到基础标签词 =====
        // 如 "服务热情" → 合并到 "服务"，"环境整洁" → 合并到 "环境"
        stats = mergeCompoundIntoBase(stats, tagNames);

        // 过滤停用词
        stats = stats.stream()
                .filter(s -> {
                    if (s.word == null || s.word.isBlank()) return false;
                    if (STOP_WORDS.contains(s.word.trim())) return false;
                    String t = s.word.trim();
                    if (t.length() < 2 || t.length() > 15) return false;
                    if (t.matches("^[\\d.]+$") || t.matches("^[^\\w\\u4e00-\\u9fa5]+$")) return false;
                    return true;
                })
                .collect(Collectors.toList());

        // 计算热度并写入
        long daysFromMidpoint = ChronoUnit.DAYS.between(
                startDate.plusDays((ChronoUnit.DAYS.between(startDate, endDate)) / 2),
                LocalDate.now());
        double recencyFactor = 1.0 / (1.0 + (double) daysFromMidpoint / DECAY_DAYS);

        List<RegionHotWord> hotWords = new ArrayList<>();
        for (WordStat stat : stats) {
            BigDecimal sentimentWeight = switch (stat.sentiment != null ? stat.sentiment : "NEUTRAL") {
                case "POSITIVE" -> POSITIVE_WEIGHT;
                case "NEGATIVE" -> NEGATIVE_WEIGHT;
                default -> NEUTRAL_WEIGHT;
            };
            double rawScore = stat.mentionCount * recencyFactor * sentimentWeight.doubleValue();
            double normalized = Math.min(rawScore * 100.0 / 200.0, 100.0);
            BigDecimal heatScore = BigDecimal.valueOf(normalized).setScale(2, RoundingMode.HALF_UP);
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

        hotWords.sort((a, b) -> b.getHeatScore().compareTo(a.getHeatScore()));
        if (hotWords.size() > 100) {
            hotWords = hotWords.subList(0, 100);
        }

        for (RegionHotWord hw : hotWords) {
            try {
                regionHotWordMapper.insert(hw);
            } catch (Exception e) {
                log.debug("插入热词失败(跳过): word={}, error={}", hw.getWord(), e.getMessage());
            }
        }

        return hotWords.size();
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
     * 加载所有活跃标签的名称集合。
     */
    private Set<String> loadTagNames() {
        String sql = "SELECT name FROM review_tags WHERE status = 'ACTIVE'";
        return new LinkedHashSet<>(jdbcTemplate.queryForList(sql, String.class));
    }

    /**
     * 将复合关键词合并到其包含的基础标签词中。
     *
     * 例如 "服务热情" 包含标签 "服务" → 将 "服务热情" 的 mentionCount
     * 加到同 sentiment 的 "服务" 上，然后移除 "服务热情"。
     * 只合并 sentiment 相同的条目，不同 sentiment 的保留原样（如 "服务热情" 正面
     * 不应合并到 "服务" 负面）。
     */
    private List<WordStat> mergeCompoundIntoBase(List<WordStat> stats, Set<String> tagNames) {
        // 按 sentiment 分组处理
        Map<String, List<WordStat>> bySentiment = stats.stream()
                .collect(Collectors.groupingBy(s -> s.sentiment != null ? s.sentiment : "NEUTRAL",
                        LinkedHashMap::new, Collectors.toList()));

        List<WordStat> result = new ArrayList<>();
        for (var entry : bySentiment.entrySet()) {
            List<WordStat> group = entry.getValue();
            // 建立 word → stat 索引
            Map<String, WordStat> index = new LinkedHashMap<>();
            for (WordStat s : group) {
                index.put(s.word, s);
            }

            for (WordStat s : group) {
                // 如果这个词本身就是一个标签名，或者不包含任何标签名，保留
                if (tagNames.contains(s.word) || !containsAnyTag(s.word, tagNames)) {
                    // 不重复添加（可能在上面的循环中已处理）
                    continue;
                }
                // 找到被包含的标签名
                String baseTag = findContainedTag(s.word, tagNames);
                if (baseTag != null) {
                    WordStat base = index.get(baseTag);
                    if (base != null) {
                        // 合并：取较大值来估算真实提及次数
                        base.mentionCount = Math.max(base.mentionCount, s.mentionCount);
                        base.reviewCount = Math.max(base.reviewCount, s.reviewCount);
                        base.merchantCount = Math.max(base.merchantCount, s.merchantCount);
                        // 如果基础词来自标签，保持 sourceType = AI_TAG
                        if ("AI_TAG".equals(base.sourceType)) {
                            // keep
                        }
                        // 从索引中移除复合词
                        index.remove(s.word);
                    }
                }
            }

            result.addAll(index.values());
        }

        return result;
    }

    private boolean containsAnyTag(String word, Set<String> tagNames) {
        return findContainedTag(word, tagNames) != null;
    }

    /**
     * 找到被 word 包含的最长标签名。如 word="服务热情" → 返回 "服务"。
     */
    private String findContainedTag(String word, Set<String> tagNames) {
        String best = null;
        for (String tag : tagNames) {
            if (!word.equals(tag) && word.contains(tag)) {
                if (best == null || tag.length() > best.length()) {
                    best = tag;
                }
            }
        }
        return best;
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
                  AND m.operation_status = 'OPERATING'
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
