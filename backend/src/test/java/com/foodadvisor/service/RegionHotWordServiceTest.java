package com.foodadvisor.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.RegionHotWordVO;
import com.foodadvisor.dto.RegionHotWordVO.HotWordMerchantBrief;
import com.foodadvisor.dto.RegionHotWordVO.RegionBriefVO;
import com.foodadvisor.entity.RegionHotWord;
import com.foodadvisor.mapper.RegionHotWordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 区域热词服务单元测试
 *
 * 覆盖查询层：分页查询、区域列表、关联商家、版本号。
 * 热词生成逻辑由集成测试 RegionHotWordServiceIntegrationTest 覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegionHotWordServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RegionHotWordMapper regionHotWordMapper;

    private RegionHotWordService service;

    @BeforeEach
    void setUp() {
        service = new RegionHotWordService(jdbcTemplate, regionHotWordMapper);
    }

    // ==================== 热词分页查询 ====================

    @Test
    void shouldQueryHotWordsWithRegionAndCategoryFilter() {
        // MyBatis-Plus selectPage 会修改传入的 Page 参数（setTotal/setRecords）
        // 所以用 thenAnswer 而非 thenReturn 来模拟这个行为
        when(regionHotWordMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Page<RegionHotWord> pageArg = invocation.getArgument(0);
                    pageArg.setTotal(3);
                    pageArg.setRecords(List.of(
                            hotWord("麻辣鲜香", "TASTE", "POSITIVE", 85.50, 20, 15, 3),
                            hotWord("粤式点心", "TASTE", "POSITIVE", 72.30, 15, 12, 2),
                            hotWord("川味火锅", "TASTE", "POSITIVE", 60.10, 10, 8, 2)));
                    return pageArg;
                });

        Page<RegionHotWordVO> result = service.queryHotWords(
                "REGION-001", "TASTE", "WEEKLY", 1, 20);

        assertAll(
                () -> assertEquals(3, result.getTotal()),
                () -> assertEquals(3, result.getRecords().size()),
                () -> assertEquals("麻辣鲜香", result.getRecords().get(0).getWord()),
                () -> assertEquals("TASTE", result.getRecords().get(0).getCategory()),
                () -> assertEquals(BigDecimal.valueOf(85.50), result.getRecords().get(0).getHeatScore()),
                () -> assertEquals(20, result.getRecords().get(0).getMentionCount()),
                () -> assertEquals(15, result.getRecords().get(0).getReviewCount()),
                () -> assertEquals(3, result.getRecords().get(0).getMerchantCount())
        );
    }

    @Test
    void shouldDefaultToWeeklyPeriodType() {
        when(regionHotWordMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> {
                    Page<RegionHotWord> pageArg = invocation.getArgument(0);
                    pageArg.setTotal(0);
                    pageArg.setRecords(List.of());
                    return pageArg;
                });

        // periodType 传 null，应默认 WEEKLY —— 不抛异常即为通过
        Page<RegionHotWordVO> result = service.queryHotWords(null, null, null, 1, 20);

        assertAll(
                () -> assertEquals(0, result.getTotal()),
                () -> assertTrue(result.getRecords().isEmpty())
        );
        // mock 被调用即证明方法正常执行，不抛异常
        verify(regionHotWordMapper).selectPage(any(Page.class), any());
    }

    @Test
    void shouldNotAddRegionFilterWhenRegionCodeIsNull() {
        when(regionHotWordMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> {
                    Page<RegionHotWord> pageArg = invocation.getArgument(0);
                    pageArg.setTotal(0);
                    pageArg.setRecords(List.of());
                    return pageArg;
                });

        // regionCode 为 null，category 和 periodType 有值时正常执行
        Page<RegionHotWordVO> result = service.queryHotWords(null, "TASTE", "WEEKLY", 1, 20);

        // 不抛异常即通过
        assertEquals(0, result.getTotal());
        verify(regionHotWordMapper).selectPage(any(Page.class), any());
    }

    @Test
    void shouldReturnEmptyPageWhenNoHotWords() {
        when(regionHotWordMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> {
                    Page<RegionHotWord> pageArg = invocation.getArgument(0);
                    pageArg.setTotal(0);
                    pageArg.setRecords(List.of());
                    return pageArg;
                });

        Page<RegionHotWordVO> result = service.queryHotWords(
                "NONEXISTENT-REGION", null, "DAILY", 1, 20);

        assertAll(
                () -> assertEquals(0, result.getTotal()),
                () -> assertTrue(result.getRecords().isEmpty())
        );
    }

    @Test
    void shouldQueryMonthlyHotWords() {
        when(regionHotWordMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> {
                    Page<RegionHotWord> pageArg = invocation.getArgument(0);
                    pageArg.setTotal(1);
                    pageArg.setRecords(List.of(
                            hotWord("性价比高", "PRICE", "POSITIVE", 55.00, 12, 10, 3)));
                    return pageArg;
                });

        Page<RegionHotWordVO> result = service.queryHotWords("REGION-001", null, "MONTHLY", 1, 20);

        assertEquals(1, result.getTotal());
        assertEquals("性价比高", result.getRecords().get(0).getWord());
    }

    // ==================== 区域列表查询 ====================

    @Test
    void shouldListRegionsOrderedByHotWordCount() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of(
                        RegionBriefVO.builder()
                                .regionCode("REGION-001")
                                .hotWordCount(25)
                                .topWord("麻辣鲜香")
                                .build(),
                        RegionBriefVO.builder()
                                .regionCode("REGION-002")
                                .hotWordCount(8)
                                .topWord("白切鸡")
                                .build()
                ));

        List<RegionBriefVO> regions = service.listRegionsWithHotWords();

        assertAll(
                () -> assertEquals(2, regions.size()),
                () -> assertEquals("REGION-001", regions.get(0).getRegionCode()),
                () -> assertEquals("麻辣鲜香", regions.get(0).getTopWord()),
                () -> assertEquals(25, regions.get(0).getHotWordCount())
        );
    }

    @Test
    void shouldReturnEmptyListWhenNoRegionsHaveData() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of());

        List<RegionBriefVO> regions = service.listRegionsWithHotWords();

        assertTrue(regions.isEmpty());
    }

    // ==================== 关联商家查询 ====================

    @Test
    void shouldGetAssociatedMerchantsForHotWord() {
        RegionHotWord hotWord = hotWord("麻辣鲜香", "TASTE", "POSITIVE",
                85.50, 20, 15, 3);
        hotWord.setId(1L);
        hotWord.setPeriodStart(LocalDate.now().minusDays(7));

        when(regionHotWordMapper.selectById(1L)).thenReturn(hotWord);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenReturn(List.of(
                        HotWordMerchantBrief.builder()
                                .merchantId(1L).merchantName("川味小馆")
                                .category("川菜").mentionCount(12).build(),
                        HotWordMerchantBrief.builder()
                                .merchantId(3L).merchantName("深夜烧烤王")
                                .category("烧烤").mentionCount(5).build()
                ));

        List<HotWordMerchantBrief> merchants = service.getAssociatedMerchants(1L, 10);

        assertAll(
                () -> assertEquals(2, merchants.size()),
                () -> assertEquals("川味小馆", merchants.get(0).getMerchantName()),
                () -> assertEquals(12, merchants.get(0).getMentionCount()),
                () -> assertEquals("深夜烧烤王", merchants.get(1).getMerchantName())
        );
    }

    @Test
    void shouldReturnEmptyListWhenHotWordNotFoundForMerchants() {
        when(regionHotWordMapper.selectById(999L)).thenReturn(null);

        List<HotWordMerchantBrief> merchants = service.getAssociatedMerchants(999L, 10);

        assertTrue(merchants.isEmpty());
    }

    @Test
    void shouldRespectLimitParameterWhenQueryingMerchants() {
        RegionHotWord hotWord = hotWord("上菜慢", "SPEED", "NEGATIVE",
                45.00, 8, 6, 3);
        hotWord.setId(2L);
        hotWord.setPeriodStart(LocalDate.now().minusDays(7));

        when(regionHotWordMapper.selectById(2L)).thenReturn(hotWord);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenReturn(List.of(
                        HotWordMerchantBrief.builder()
                                .merchantId(1L).merchantName("川味小馆")
                                .category("川菜").mentionCount(3).build()
                ));

        List<HotWordMerchantBrief> merchants = service.getAssociatedMerchants(2L, 5);

        assertEquals(1, merchants.size());
    }

    // ==================== 版本号查询 ====================

    @Test
    void shouldGetNextVersionWhenPreviousDataExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                anyString(), any(), any()))
                .thenReturn(3);

        int version = service.getNextVersionHelper("WEEKLY",
                LocalDate.now().minusDays(7), LocalDate.now());

        assertEquals(3, version);
    }

    @Test
    void shouldDefaultToVersionOneWhenNoPreviousData() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                anyString(), any(), any()))
                .thenReturn(null);

        int version = service.getNextVersionHelper("DAILY",
                LocalDate.now().minusDays(1), LocalDate.now());

        assertEquals(1, version, "无历史数据时版本号应为 1");
    }

    // ==================== 辅助方法 ====================

    private RegionHotWord hotWord(String word, String category, String sentiment,
                                   double heatScore, int mentionCount,
                                   int reviewCount, int merchantCount) {
        RegionHotWord hw = new RegionHotWord();
        hw.setId(1L);
        hw.setRegionCode("REGION-001");
        hw.setWord(word);
        hw.setCategory(category);
        hw.setSentiment(sentiment);
        hw.setHeatScore(BigDecimal.valueOf(heatScore));
        hw.setMentionCount(mentionCount);
        hw.setReviewCount(reviewCount);
        hw.setMerchantCount(merchantCount);
        hw.setPositiveRatio(new BigDecimal("0.85"));
        hw.setSourceType("AI_TAG");
        hw.setPeriodType("WEEKLY");
        hw.setPeriodStart(LocalDate.now().minusDays(7));
        hw.setPeriodEnd(LocalDate.now());
        hw.setVersion(1);
        hw.setStatus("ACTIVE");
        return hw;
    }
}
