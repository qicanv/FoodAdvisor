package com.foodadvisor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.feedback.AnalysisFeedbackStatisticsVO;
import com.foodadvisor.dto.feedback.AnalysisFeedbackSubmitRequest;
import com.foodadvisor.dto.feedback.AnalysisFeedbackVO;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.service.AnalysisFeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AnalysisFeedbackController 单元测试（EPIC-06 Story 5）
 *
 * 覆盖商家端和管理员端全部 5 个端点。
 */
@ExtendWith(MockitoExtension.class)
class AnalysisFeedbackControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AnalysisFeedbackService feedbackService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---- 商家用户认证属性 ----
    private static final Long MERCHANT_USER_ID = 10L;
    private static final String MERCHANT_USERNAME = "merchant01";
    private static final String MERCHANT_ROLE = "MERCHANT";

    // ---- 管理员认证属性 ----
    private static final Long ADMIN_USER_ID = 1L;
    private static final String ADMIN_ROLE = "ADMIN";

    @BeforeEach
    void setUp() {
        AnalysisFeedbackController controller =
                new AnalysisFeedbackController(feedbackService, jdbcTemplate);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ============================================================
    // 商家端：POST /api/merchant-console/merchants/{id}/analysis-feedback
    // ============================================================

    @Test
    void shouldSubmitFeedbackAsMerchant() throws Exception {
        // 模拟商家成员关系
        mockMerchantMember(MERCHANT_USER_ID, 1L);
        AnalysisFeedbackVO vo = buildVO(1L, "SENTIMENT", 101L, "ACCURATE", "分析准确");
        when(feedbackService.submitFeedback(eq(1L), eq(MERCHANT_USER_ID), any()))
                .thenReturn(vo);

        AnalysisFeedbackSubmitRequest req = new AnalysisFeedbackSubmitRequest();
        req.setAnalysisType("SENTIMENT");
        req.setAnalysisId(101L);
        req.setFeedbackType("ACCURATE");
        req.setContent("分析准确");

        mockMvc.perform(post("/api/merchant-console/merchants/1/analysis-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr("userId", MERCHANT_USER_ID)
                        .requestAttr("role", MERCHANT_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("反馈提交成功"))
                .andExpect(jsonPath("$.data.analysisType").value("SENTIMENT"))
                .andExpect(jsonPath("$.data.feedbackType").value("ACCURATE"))
                .andExpect(jsonPath("$.data.analysisTypeText").value("情感分析"))
                .andExpect(jsonPath("$.data.feedbackTypeText").value("准确"));
    }

    @Test
    void shouldDenySubmitWithoutJwtAuth() throws Exception {
        AnalysisFeedbackSubmitRequest req = new AnalysisFeedbackSubmitRequest();
        req.setAnalysisType("SENTIMENT");
        req.setFeedbackType("ACCURATE");

        // 不设置 userId 属性 → 模拟未认证
        mockMvc.perform(post("/api/merchant-console/merchants/1/analysis-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldDenySubmitForNonMember() throws Exception {
        // 用户不是商家成员
        when(jdbcTemplate.queryForList(anyString(), eq(10L), eq(99L)))
                .thenReturn(List.of()); // ← 空列表 = 不是成员

        AnalysisFeedbackSubmitRequest req = validSubmitRequest();

        mockMvc.perform(post("/api/merchant-console/merchants/99/analysis-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .requestAttr("userId", 10L)
                        .requestAttr("role", "MERCHANT"))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // AC-5: 商家用户查询其他店铺反馈时返回 403
    // ============================================================

    @Test
    void shouldDenyListForNonMember() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), eq(10L), eq(99L)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/merchant-console/merchants/99/analysis-feedback")
                        .requestAttr("userId", 10L)
                        .requestAttr("role", "MERCHANT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldDenyDetailForNonMember() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), eq(10L), eq(99L)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/merchant-console/merchants/99/analysis-feedback/5")
                        .requestAttr("userId", 10L)
                        .requestAttr("role", "MERCHANT"))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 商家端：GET /api/merchant-console/merchants/{id}/analysis-feedback
    // ============================================================

    @Test
    void shouldListMerchantFeedbackWithFilters() throws Exception {
        mockMerchantMember(MERCHANT_USER_ID, 1L);
        AnalysisFeedbackVO vo = buildVO(1L, "KEYWORD", 201L, "INACCURATE", "遗漏了关键特征词");
        PageResult<AnalysisFeedbackVO> pageResult = new PageResult<>(1, 20, 1, List.of(vo));
        when(feedbackService.getMerchantFeedback(eq(1L), eq("KEYWORD"), eq("INACCURATE"), eq(1), eq(20)))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/merchant-console/merchants/1/analysis-feedback")
                        .param("analysisType", "KEYWORD")
                        .param("feedbackType", "INACCURATE")
                        .requestAttr("userId", MERCHANT_USER_ID)
                        .requestAttr("role", MERCHANT_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].analysisType").value("KEYWORD"))
                .andExpect(jsonPath("$.data.records[0].feedbackType").value("INACCURATE"))
                .andExpect(jsonPath("$.data.records[0].content").value("遗漏了关键特征词"));
    }

    @Test
    void shouldListMerchantFeedbackWithDefaultPagination() throws Exception {
        mockMerchantMember(MERCHANT_USER_ID, 1L);
        PageResult<AnalysisFeedbackVO> pageResult = new PageResult<>(1, 20, 0, List.of());
        when(feedbackService.getMerchantFeedback(eq(1L), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/merchant-console/merchants/1/analysis-feedback")
                        .requestAttr("userId", MERCHANT_USER_ID)
                        .requestAttr("role", MERCHANT_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ============================================================
    // 商家端：GET /api/merchant-console/merchants/{id}/analysis-feedback/{fid}
    // ============================================================

    @Test
    void shouldGetFeedbackDetail() throws Exception {
        mockMerchantMember(MERCHANT_USER_ID, 1L);
        AnalysisFeedbackVO vo = buildVO(1L, "COMPETITOR", 301L, "ACCURATE",
                "对比维度全面，数据准确");
        when(feedbackService.getFeedbackDetail(5L, 1L)).thenReturn(vo);

        mockMvc.perform(get("/api/merchant-console/merchants/1/analysis-feedback/5")
                        .requestAttr("userId", MERCHANT_USER_ID)
                        .requestAttr("role", MERCHANT_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));
    }

    // ============================================================
    // 管理员端：GET /api/admin/analysis-feedback/statistics
    // ============================================================

    @Test
    void shouldReturnStatisticsForAdmin() throws Exception {
        AnalysisFeedbackStatisticsVO stats = AnalysisFeedbackStatisticsVO.builder()
                .totalCount(100L)
                .accurateCount(75L)
                .inaccurateCount(25L)
                .accuracyRate(0.75)
                .byAnalysisType(List.of(
                        AnalysisFeedbackStatisticsVO.AnalysisTypeStat.builder()
                                .analysisType("SENTIMENT")
                                .analysisTypeText("情感分析")
                                .totalCount(50L)
                                .accurateCount(40L)
                                .inaccurateCount(10L)
                                .accuracyRate(0.8)
                                .build()
                ))
                .build();
        when(feedbackService.getStatistics()).thenReturn(stats);

        mockMvc.perform(get("/api/admin/analysis-feedback/statistics")
                        .requestAttr("userId", ADMIN_USER_ID)
                        .requestAttr("role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalCount").value(100))
                .andExpect(jsonPath("$.data.accurateCount").value(75))
                .andExpect(jsonPath("$.data.inaccurateCount").value(25))
                .andExpect(jsonPath("$.data.accuracyRate").value(0.75))
                .andExpect(jsonPath("$.data.byAnalysisType[0].analysisType").value("SENTIMENT"))
                .andExpect(jsonPath("$.data.byAnalysisType[0].analysisTypeText").value("情感分析"));
    }

    @Test
    void shouldAllowOperatorToAccessStatistics() throws Exception {
        when(feedbackService.getStatistics()).thenReturn(
                AnalysisFeedbackStatisticsVO.builder()
                        .totalCount(0L).accurateCount(0L).inaccurateCount(0L)
                        .byAnalysisType(List.of()).build());

        mockMvc.perform(get("/api/admin/analysis-feedback/statistics")
                        .requestAttr("userId", 2L)
                        .requestAttr("role", "OPERATOR"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "MERCHANT"})
    void shouldDenyStatisticsForNonAdmin(String role) throws Exception {
        mockMvc.perform(get("/api/admin/analysis-feedback/statistics")
                        .requestAttr("userId", 10L)
                        .requestAttr("role", role))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 管理员端：GET /api/admin/analysis-feedback
    // ============================================================

    @Test
    void shouldListAllFeedbackForAdmin() throws Exception {
        AnalysisFeedbackVO vo = buildVO(1L, "BUSINESS_SUGGESTION", 401L, "INACCURATE",
                "建议不切实际，没考虑小店人力");
        vo.setMerchantName("川味小馆");
        vo.setCreatedByUsername("merchant01");
        PageResult<AnalysisFeedbackVO> pageResult = new PageResult<>(1, 20, 1, List.of(vo));
        when(feedbackService.getAllFeedback(
                isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/admin/analysis-feedback")
                        .requestAttr("userId", ADMIN_USER_ID)
                        .requestAttr("role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].merchantName").value("川味小馆"))
                .andExpect(jsonPath("$.data.records[0].createdByUsername").value("merchant01"));
    }

    @Test
    void shouldFilterAllFeedbackByMerchantId() throws Exception {
        PageResult<AnalysisFeedbackVO> pageResult = new PageResult<>(1, 20, 0, List.of());
        when(feedbackService.getAllFeedback(
                isNull(), isNull(), eq(5L), eq(1), eq(20)))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/admin/analysis-feedback")
                        .param("merchantId", "5")
                        .requestAttr("userId", ADMIN_USER_ID)
                        .requestAttr("role", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void shouldDenyAdminListForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/analysis-feedback")
                        .requestAttr("userId", 10L)
                        .requestAttr("role", "USER"))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private void mockMerchantMember(Long userId, Long merchantId) {
        when(jdbcTemplate.queryForList(
                "SELECT 1 FROM merchant_members WHERE user_id = ? AND merchant_id = ? AND status = 'ACTIVE' LIMIT 1",
                userId, merchantId))
                .thenReturn(List.of(Map.of()));
    }

    private AnalysisFeedbackSubmitRequest validSubmitRequest() {
        AnalysisFeedbackSubmitRequest req = new AnalysisFeedbackSubmitRequest();
        req.setAnalysisType("SENTIMENT");
        req.setAnalysisId(101L);
        req.setFeedbackType("ACCURATE");
        req.setContent("分析准确");
        return req;
    }

    private AnalysisFeedbackVO buildVO(Long merchantId, String analysisType,
                                        Long analysisId, String feedbackType, String content) {
        return AnalysisFeedbackVO.builder()
                .id(5L)
                .merchantId(merchantId)
                .analysisType(analysisType)
                .analysisTypeText(mapTypeText(analysisType))
                .analysisId(analysisId)
                .feedbackType(feedbackType)
                .feedbackTypeText("ACCURATE".equals(feedbackType) ? "准确" : "不准确")
                .content(content)
                .createdBy(MERCHANT_USER_ID)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private static String mapTypeText(String type) {
        return switch (type) {
            case "SENTIMENT" -> "情感分析";
            case "KEYWORD" -> "关键词提取";
            case "ISSUE_ATTRIBUTION" -> "差评归因";
            case "COMPETITOR" -> "竞品对比";
            case "BUSINESS_SUGGESTION" -> "经营建议";
            case "REVIEW_SUMMARY" -> "评价摘要";
            case "HIGHLIGHT" -> "商家亮点";
            default -> type;
        };
    }
}
