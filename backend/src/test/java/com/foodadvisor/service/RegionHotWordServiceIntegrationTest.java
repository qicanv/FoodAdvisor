package com.foodadvisor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.RegionHotWordVO;
import com.foodadvisor.dto.RegionHotWordVO.RegionBriefVO;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 区域热词集成测试 —— 使用真实 PostgreSQL 数据库
 *
 * 验证端到端流程：数据聚合 → 停用词过滤 → 热度计算 → 写入数据库 → API 查询。
 */
@SpringBootTest(properties = "foodadvisor.hot-words.scheduled.enabled=false")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegionHotWordServiceIntegrationTest {

    /** 加载 .env 文件，确保数据库密码可用 */
    static {
        Dotenv.configure().directory("./").ignoreIfMissing().systemProperties().load();
        Dotenv.configure().directory("../").ignoreIfMissing().systemProperties().load();
    }

    @Autowired
    private RegionHotWordService hotWordService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String testRegionCode;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
        testRegionCode = "TEST-HOTWORD-" + uniqueSuffix;

        Long userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (username, password_hash, nickname, role, status)
                VALUES (?, ?, ?, 'USER', 'ACTIVE')
                RETURNING id
                """, Long.class,
                "hotword_user_" + uniqueSuffix.toLowerCase(Locale.ROOT),
                "$2a$10$test.placeholder.hash.not.used.for.login",
                "热词测试用户");

        Long merchantId = jdbcTemplate.queryForObject("""
                INSERT INTO merchants (
                    merchant_code, name, category, region_code, address,
                    platform_status, operation_status
                )
                VALUES (?, ?, '川菜', ?, '测试地址', 'ACTIVE', 'OPERATING')
                RETURNING id
                """, Long.class,
                "HOTWORD-" + uniqueSuffix,
                "热词测试商家-" + uniqueSuffix,
                testRegionCode);

        Long reviewId = jdbcTemplate.queryForObject("""
                INSERT INTO reviews (
                    merchant_id, user_id, review_type, rating, content, source,
                    current_version, status, moderation_status, risk_level,
                    published_at, created_at, updated_at
                )
                VALUES (
                    ?, ?, 'ORIGINAL', 5, ?, 'SYSTEM',
                    1, 'PUBLISHED', 'APPROVED', 'LOW',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                RETURNING id
                """, Long.class,
                merchantId,
                userId,
                "麻辣鲜香，服务热情，适合作为区域热词集成测试评价。");

        jdbcTemplate.update("""
                INSERT INTO review_analysis (
                    review_id, review_version, analysis_version, sentiment,
                    confidence, low_confidence, keywords, aspects, status,
                    created_at, updated_at
                )
                VALUES (
                    ?, 1, 1, 'POSITIVE',
                    0.9500, FALSE, CAST(? AS jsonb), CAST(? AS jsonb), 'SUCCESS',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                reviewId,
                "[\"麻辣鲜香\", \"服务热情\"]",
                "[]");
    }

    // ==================== 核心生成测试 ====================

    @Test
    @Order(1)
    @DisplayName("全量生成热词 — 应为有评价数据的区域生成热词")
    void shouldGenerateHotWordsForAllRegions() {
        int count = hotWordService.regenerateAll("WEEKLY", 7);

        assertTrue(count > 0,
                "应为至少一个区域生成热词，实际生成: " + count);

        Integer activeRows = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM region_hot_words
                        WHERE status = 'ACTIVE' AND region_code = ?
                        """,
                Integer.class,
                testRegionCode);
        assertNotNull(activeRows);
        assertTrue(activeRows > 0,
                "测试区域应有 ACTIVE 状态的热词，实际: " + activeRows);
    }

    @Test
    @Order(2)
    @DisplayName("热度分数应在 0-100 范围内")
    void shouldHaveHeatScoreInValidRange() {
        hotWordService.regenerateAll("WEEKLY", 7);

        List<BigDecimal> scores = jdbcTemplate.queryForList(
                """
                        SELECT heat_score
                        FROM region_hot_words
                        WHERE status = 'ACTIVE' AND region_code = ?
                        """,
                BigDecimal.class,
                testRegionCode);

        assertFalse(scores.isEmpty(), "应有热词数据");
        for (BigDecimal score : scores) {
            assertTrue(score.compareTo(BigDecimal.ZERO) >= 0,
                    "热度分数应 >= 0，实际: " + score);
            assertTrue(score.compareTo(new BigDecimal("100.00")) <= 0,
                    "热度分数应 <= 100，实际: " + score);
        }
    }

    @Test
    @Order(3)
    @DisplayName("停用词应被过滤")
    void shouldFilterStopWords() {
        hotWordService.regenerateAll("WEEKLY", 7);

        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM region_hot_words
                        WHERE status = 'ACTIVE'
                          AND region_code = ?
                          AND word = '好吃'
                        """,
                Integer.class,
                testRegionCode);
        assertEquals(0, count, "停用词 '好吃' 不应出现");
    }

    @Test
    @Order(4)
    @DisplayName("生成的热词应包含必要字段")
    void shouldHaveRequiredFields() {
        hotWordService.regenerateAll("WEEKLY", 7);

        var rows = jdbcTemplate.queryForList(
                """
                        SELECT *
                        FROM region_hot_words
                        WHERE status = 'ACTIVE' AND region_code = ?
                        LIMIT 1
                        """,
                testRegionCode);

        assertFalse(rows.isEmpty(), "测试区域应生成至少一条热词");
        Map<String, Object> row = rows.get(0);
        assertEquals(testRegionCode, row.get("region_code"));
        assertNotNull(row.get("word"));
        assertNotNull(row.get("category"));
        assertNotNull(row.get("heat_score"));
        assertNotNull(row.get("mention_count"));
        assertNotNull(row.get("review_count"));
        assertNotNull(row.get("merchant_count"));
        assertNotNull(row.get("period_type"));
    }

    // ==================== 查询测试 ====================

    @Test
    @Order(5)
    @DisplayName("可按区域筛选热词")
    void shouldQueryHotWordsByRegion() {
        hotWordService.regenerateAll("WEEKLY", 7);

        Page<RegionHotWordVO> page = hotWordService.queryHotWords(
                testRegionCode, null, "WEEKLY", 1, 20);

        assertTrue(page.getTotal() > 0, "测试区域应有热词数据");
        for (RegionHotWordVO vo : page.getRecords()) {
            assertEquals(testRegionCode, vo.getRegionCode());
        }
    }

    @Test
    @Order(6)
    @DisplayName("无数据区域返回空")
    void shouldReturnEmptyForNonexistentRegion() {
        Page<RegionHotWordVO> page = hotWordService.queryHotWords(
                testRegionCode + "-NONE", null, "WEEKLY", 1, 20);

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    @Order(7)
    @DisplayName("区域列表应有数据")
    void shouldListRegions() {
        hotWordService.regenerateAll("WEEKLY", 7);

        List<RegionBriefVO> regions = hotWordService.listRegionsWithHotWords();

        assertFalse(regions.isEmpty(), "应有区域数据");
        RegionBriefVO testRegion = regions.stream()
                .filter(region -> testRegionCode.equals(region.getRegionCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("区域列表应包含本测试区域"));
        assertTrue(testRegion.getHotWordCount() > 0);
        assertNotNull(testRegion.getTopWord());
    }

    // ==================== 版本管理测试 ====================

    @Test
    @Order(8)
    @DisplayName("版本号应递增")
    void shouldIncrementVersion() {
        // 用 WEEKLY 周期（已验证可正常生成数据）
        hotWordService.regenerateAll("WEEKLY", 7);
        Integer v1 = jdbcTemplate.queryForObject(
                """
                        SELECT MAX(version)
                        FROM region_hot_words
                        WHERE period_type = 'WEEKLY' AND region_code = ?
                        """,
                Integer.class,
                testRegionCode);

        hotWordService.regenerateAll("WEEKLY", 7);
        Integer v2 = jdbcTemplate.queryForObject(
                """
                        SELECT MAX(version)
                        FROM region_hot_words
                        WHERE period_type = 'WEEKLY'
                          AND status = 'ACTIVE'
                          AND region_code = ?
                        """,
                Integer.class,
                testRegionCode);

        assertNotNull(v1, "首次生成后应有版本号");
        assertNotNull(v2, "再次生成后应有版本号");
        assertTrue(v2 > v1, "版本号应递增: v1=" + v1 + ", v2=" + v2);
    }

    // ==================== 不同周期测试 ====================

    @Test
    @Order(9)
    @DisplayName("支持 DAILY、WEEKLY、MONTHLY 三种周期")
    void shouldSupportAllPeriodTypes() {
        int daily = hotWordService.regenerateAll("DAILY", 1);
        int weekly = hotWordService.regenerateAll("WEEKLY", 7);
        int monthly = hotWordService.regenerateAll("MONTHLY", 30);

        assertAll(
                () -> assertTrue(daily > 0, "DAILY 应生成热词"),
                () -> assertTrue(weekly > 0, "WEEKLY 应生成热词"),
                () -> assertTrue(monthly > 0, "MONTHLY 应生成热词")
        );
    }
}
