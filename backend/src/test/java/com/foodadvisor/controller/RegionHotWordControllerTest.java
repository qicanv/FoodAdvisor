package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.RegionHotWordVO;
import com.foodadvisor.dto.RegionHotWordVO.HotWordMerchantBrief;
import com.foodadvisor.dto.RegionHotWordVO.RegionBriefVO;
import com.foodadvisor.service.RegionHotWordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 区域热词控制器 WebMvcTest
 *
 * 覆盖全部 5 个 REST 端点：
 * - GET  /api/hot-words                    — 分页查询热词
 * - GET  /api/hot-words/regions            — 获取区域列表
 * - GET  /api/hot-words/{id}/merchants     — 获取关联商家
 * - POST /api/admin/hot-words/regenerate   — 全量触发生成
 * - POST /api/admin/hot-words/regenerate/{regionCode} — 单区域触发生成
 */
@WebMvcTest(RegionHotWordController.class)
class RegionHotWordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionHotWordService hotWordService;

    /** GlobalExceptionHandler 依赖 AuditLogService 和 SensitiveLogSanitizer，必须 Mock */
    @MockitoBean
    private com.foodadvisor.service.AuditLogService auditLogService;

    @MockitoBean
    private com.foodadvisor.util.SensitiveLogSanitizer sensitiveLogSanitizer;

    // ==================== GET /api/hot-words ====================

    @Test
    void shouldReturnHotWordListWithDefaultParams() throws Exception {
        Page<RegionHotWordVO> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(buildVO("麻辣鲜香", "TASTE", 85.5)));

        when(hotWordService.queryHotWords(isNull(), isNull(), isNull(),
                eq(1), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/hot-words"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].word").value("麻辣鲜香"))
                .andExpect(jsonPath("$.data.records[0].category").value("TASTE"))
                .andExpect(jsonPath("$.data.records[0].heatScore").value(85.5));
    }

    @Test
    void shouldFilterByRegionAndCategory() throws Exception {
        Page<RegionHotWordVO> page = new Page<>(1, 10, 0);
        page.setRecords(List.of());

        when(hotWordService.queryHotWords(eq("REGION-001"), eq("TASTE"),
                eq("WEEKLY"), eq(1), eq(10)))
                .thenReturn(page);

        mockMvc.perform(get("/api/hot-words")
                        .param("regionCode", "REGION-001")
                        .param("category", "TASTE")
                        .param("periodType", "WEEKLY")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void shouldHandleEmptyResult() throws Exception {
        Page<RegionHotWordVO> page = new Page<>(1, 20, 0);
        page.setRecords(List.of());

        when(hotWordService.queryHotWords(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/hot-words")
                        .param("regionCode", "EMPTY-REGION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records", hasSize(0)));
    }

    // ==================== GET /api/hot-words/regions ====================

    @Test
    void shouldReturnRegionList() throws Exception {
        List<RegionBriefVO> regions = List.of(
                RegionBriefVO.builder()
                        .regionCode("REGION-001")
                        .hotWordCount(15)
                        .topWord("麻辣鲜香")
                        .build(),
                RegionBriefVO.builder()
                        .regionCode("REGION-002")
                        .hotWordCount(8)
                        .topWord("白切鸡")
                        .build()
        );

        when(hotWordService.listRegionsWithHotWords()).thenReturn(regions);

        mockMvc.perform(get("/api/hot-words/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].regionCode").value("REGION-001"))
                .andExpect(jsonPath("$.data[0].hotWordCount").value(15))
                .andExpect(jsonPath("$.data[0].topWord").value("麻辣鲜香"))
                .andExpect(jsonPath("$.data[1].regionCode").value("REGION-002"));
    }

    @Test
    void shouldReturnEmptyRegionList() throws Exception {
        when(hotWordService.listRegionsWithHotWords()).thenReturn(List.of());

        mockMvc.perform(get("/api/hot-words/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ==================== GET /api/hot-words/{id}/merchants ====================

    @Test
    void shouldReturnAssociatedMerchants() throws Exception {
        List<HotWordMerchantBrief> merchants = List.of(
                HotWordMerchantBrief.builder()
                        .merchantId(1L).merchantName("川味小馆")
                        .category("川菜").mentionCount(12).build(),
                HotWordMerchantBrief.builder()
                        .merchantId(3L).merchantName("深夜烧烤王")
                        .category("烧烤").mentionCount(5).build()
        );

        when(hotWordService.getAssociatedMerchants(1L, 10)).thenReturn(merchants);

        mockMvc.perform(get("/api/hot-words/1/merchants").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].merchantId").value(1))
                .andExpect(jsonPath("$.data[0].merchantName").value("川味小馆"))
                .andExpect(jsonPath("$.data[0].mentionCount").value(12))
                .andExpect(jsonPath("$.data[1].merchantName").value("深夜烧烤王"));
    }

    @Test
    void shouldReturnEmptyMerchantsForNonexistentHotWord() throws Exception {
        when(hotWordService.getAssociatedMerchants(999L, 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/hot-words/999/merchants").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void shouldUseDefaultLimitForMerchants() throws Exception {
        when(hotWordService.getAssociatedMerchants(1L, 10)).thenReturn(List.of());

        // 不传 limit 参数，默认 10
        mockMvc.perform(get("/api/hot-words/1/merchants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    // ==================== POST /api/admin/hot-words/regenerate ====================

    @Test
    void shouldRegenerateAllHotWords() throws Exception {
        when(hotWordService.regenerateAll("WEEKLY", 7)).thenReturn(42);

        String requestBody = """
                {
                    "periodType": "WEEKLY",
                    "daysBack": 7
                }
                """;

        mockMvc.perform(post("/api/hot-words/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("热词生成完成"))
                .andExpect(jsonPath("$.data.generatedCount").value(42))
                .andExpect(jsonPath("$.data.periodType").value("WEEKLY"))
                .andExpect(jsonPath("$.data.daysBack").value(7));
    }

    @Test
    void shouldUseDefaultValuesWhenRegenerateBodyEmpty() throws Exception {
        when(hotWordService.regenerateAll("WEEKLY", 7)).thenReturn(10);

        mockMvc.perform(post("/api/hot-words/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.generatedCount").value(10));
    }

    // ==================== POST /api/admin/hot-words/regenerate/{regionCode} ====================

    @Test
    void shouldRegenerateForSingleRegion() throws Exception {
        when(hotWordService.getNextVersionHelper(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(1);
        when(hotWordService.generateForRegion(eq("REGION-001"), eq("WEEKLY"),
                any(LocalDate.class), any(LocalDate.class), eq(1)))
                .thenReturn(15);

        String requestBody = """
                {
                    "periodType": "WEEKLY",
                    "daysBack": 7
                }
                """;

        mockMvc.perform(post("/api/hot-words/regenerate/REGION-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.generatedCount").value(15))
                .andExpect(jsonPath("$.data.regionCode").value("REGION-001"));
    }

    // ==================== 辅助方法 ====================

    private RegionHotWordVO buildVO(String word, String category, double heatScore) {
        return RegionHotWordVO.builder()
                .id(1L)
                .regionCode("REGION-001")
                .word(word)
                .category(category)
                .sentiment("POSITIVE")
                .heatScore(BigDecimal.valueOf(heatScore))
                .mentionCount(10)
                .reviewCount(8)
                .merchantCount(3)
                .positiveRatio(new BigDecimal("0.85"))
                .periodType("WEEKLY")
                .periodStart(LocalDate.now().minusDays(7))
                .periodEnd(LocalDate.now())
                .build();
    }
}
