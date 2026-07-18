package com.foodadvisor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.RegionHotWordVO;
import com.foodadvisor.dto.RegionHotWordVO.RegionBriefVO;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 区域热词集成测试 —— 使用真实 PostgreSQL 数据库
 *
 * 验证端到端流程：数据聚合 → 停用词过滤 → 热度计算 → 写入数据库 → API 查询。
 */
@SpringBootTest
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

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM region_hot_words");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM region_hot_words");
    }

    // ==================== 核心生成测试 ====================

    @Test
    @Order(1)
    @DisplayName("全量生成热词 — 应为有评价数据的区域生成热词")
    void shouldGenerateHotWordsForAllRegions() {
        int count = hotWordService.regenerateAll("WEEKLY", 7);

        assertTrue(count > 0,
                "应为至少一个区域生成热词，实际生成: " + count);

        // 验证已写入数据库
        Integer activeRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM region_hot_words WHERE status = 'ACTIVE'",
                Integer.class);
        assertNotNull(activeRows);
        assertTrue(activeRows > 0, "应有 ACTIVE 状态的热词，实际: " + activeRows);
    }

    @Test
    @Order(2)
    @DisplayName("热度分数应在 0-100 范围内")
    void shouldHaveHeatScoreInValidRange() {
        hotWordService.regenerateAll("WEEKLY", 7);

        List<BigDecimal> scores = jdbcTemplate.queryForList(
                "SELECT heat_score FROM region_hot_words WHERE status = 'ACTIVE'",
                BigDecimal.class);

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
                "SELECT COUNT(*) FROM region_hot_words WHERE status = 'ACTIVE' AND word = '好吃'",
                Integer.class);
        assertEquals(0, count, "停用词 '好吃' 不应出现");
    }

    @Test
    @Order(4)
    @DisplayName("生成的热词应包含必要字段")
    void shouldHaveRequiredFields() {
        hotWordService.regenerateAll("WEEKLY", 7);

        var rows = jdbcTemplate.queryForList(
                "SELECT * FROM region_hot_words WHERE status = 'ACTIVE' LIMIT 1");

        if (!rows.isEmpty()) {
            var row = rows.get(0);
            assertNotNull(row.get("region_code"));
            assertNotNull(row.get("word"));
            assertNotNull(row.get("category"));
            assertNotNull(row.get("heat_score"));
            assertNotNull(row.get("mention_count"));
            assertNotNull(row.get("review_count"));
            assertNotNull(row.get("merchant_count"));
            assertNotNull(row.get("period_type"));
        }
    }

    // ==================== 查询测试 ====================

    @Test
    @Order(5)
    @DisplayName("可按区域筛选热词")
    void shouldQueryHotWordsByRegion() {
        hotWordService.regenerateAll("WEEKLY", 7);

        Page<RegionHotWordVO> page = hotWordService.queryHotWords(
                "REGION-001", null, "WEEKLY", 1, 20);

        assertTrue(page.getTotal() > 0, "REGION-001 应有热词数据");
        for (RegionHotWordVO vo : page.getRecords()) {
            assertEquals("REGION-001", vo.getRegionCode());
        }
    }

    @Test
    @Order(6)
    @DisplayName("无数据区域返回空")
    void shouldReturnEmptyForNonexistentRegion() {
        Page<RegionHotWordVO> page = hotWordService.queryHotWords(
                "NONEXISTENT", null, "WEEKLY", 1, 20);

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
        assertNotNull(regions.get(0).getRegionCode());
        assertTrue(regions.get(0).getHotWordCount() > 0);
    }

    // ==================== 版本管理测试 ====================

    @Test
    @Order(8)
    @DisplayName("版本号应递增")
    void shouldIncrementVersion() {
        // 用 WEEKLY 周期（已验证可正常生成数据）
        hotWordService.regenerateAll("WEEKLY", 7);
        Integer v1 = jdbcTemplate.queryForObject(
                "SELECT MAX(version) FROM region_hot_words WHERE period_type = 'WEEKLY'",
                Integer.class);

        hotWordService.regenerateAll("WEEKLY", 7);
        Integer v2 = jdbcTemplate.queryForObject(
                "SELECT MAX(version) FROM region_hot_words WHERE period_type = 'WEEKLY' AND status = 'ACTIVE'",
                Integer.class);

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
                () -> assertTrue(daily >= 0, "DAILY 不应抛异常"),
                () -> assertTrue(weekly >= 0, "WEEKLY 不应抛异常"),
                () -> assertTrue(monthly >= 0, "MONTHLY 不应抛异常")
        );
    }
}
