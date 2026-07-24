package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.feedback.AnalysisFeedbackStatisticsVO;
import com.foodadvisor.dto.feedback.AnalysisFeedbackSubmitRequest;
import com.foodadvisor.dto.feedback.AnalysisFeedbackVO;
import com.foodadvisor.entity.AnalysisFeedback;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.User;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.AnalysisFeedbackMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AnalysisFeedbackService 单元测试（EPIC-06 Story 5）
 *
 * 覆盖全部 7 条验收准则的核心逻辑。
 */
@ExtendWith(MockitoExtension.class)
class AnalysisFeedbackServiceTest {

    @Mock AnalysisFeedbackMapper feedbackMapper;
    @Mock MerchantMapper merchantMapper;
    @Mock UserMapper userMapper;

    private AnalysisFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisFeedbackService(feedbackMapper, merchantMapper, userMapper);
    }

    // ============================================================
    // AC-1: 商家用户能够对属于自己店铺的分析结果提交反馈
    // ============================================================

    @Test
    void shouldSubmitNewFeedback() {
        AnalysisFeedbackSubmitRequest req = validRequest("SENTIMENT", 101L, "ACCURATE", "分析准确");
        when(feedbackMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AnalysisFeedbackVO result = service.submitFeedback(1L, 10L, req);

        ArgumentCaptor<AnalysisFeedback> captor = ArgumentCaptor.forClass(AnalysisFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        AnalysisFeedback saved = captor.getValue();

        assertAll(
                () -> assertEquals(1L, saved.getMerchantId()),
                () -> assertEquals("SENTIMENT", saved.getAnalysisType()),
                () -> assertEquals(101L, saved.getAnalysisId()),
                () -> assertEquals("ACCURATE", saved.getFeedbackType()),
                () -> assertEquals("分析准确", saved.getContent()),
                () -> assertEquals(10L, saved.getCreatedBy())
        );
        assertAll(
                () -> assertEquals("SENTIMENT", result.getAnalysisType()),
                () -> assertEquals("ACCURATE", result.getFeedbackType()),
                () -> assertEquals("情感分析", result.getAnalysisTypeText()),
                () -> assertEquals("准确", result.getFeedbackTypeText())
        );
    }

    @Test
    void shouldSubmitFeedbackWithoutAnalysisId() {
        AnalysisFeedbackSubmitRequest req = validRequest("BUSINESS_SUGGESTION", null, "INACCURATE", null);

        AnalysisFeedbackVO result = service.submitFeedback(1L, 10L, req);

        assertNull(result.getAnalysisId());
        verify(feedbackMapper).insert(any(AnalysisFeedback.class));
    }

    // ============================================================
    // AC-7: 同一商家对同一分析记录重复反馈时更新已有记录
    // ============================================================

    @Test
    void shouldUpdateExistingFeedbackOnResubmit() {
        AnalysisFeedbackSubmitRequest req = validRequest("SENTIMENT", 101L, "INACCURATE", "改了，不准确");
        AnalysisFeedback existing = new AnalysisFeedback();
        existing.setId(5L);
        existing.setMerchantId(1L);
        existing.setAnalysisType("SENTIMENT");
        existing.setAnalysisId(101L);
        existing.setFeedbackType("ACCURATE");
        existing.setContent("分析准确");
        existing.setCreatedBy(10L);

        when(feedbackMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        AnalysisFeedbackVO result = service.submitFeedback(1L, 10L, req);

        // 应该更新而非新增
        verify(feedbackMapper, never()).insert(any(AnalysisFeedback.class));
        verify(feedbackMapper).updateById(existing);
        assertEquals("INACCURATE", existing.getFeedbackType());
        assertEquals("改了，不准确", existing.getContent());
        assertEquals("INACCURATE", result.getFeedbackType());
    }

    @Test
    void shouldAllowMultipleOverallFeedbackWithoutAnalysisId() {
        // 没有 analysisId 时，不检查唯一性，允许多条
        AnalysisFeedbackSubmitRequest req1 = validRequest("REVIEW_SUMMARY", null, "ACCURATE", null);
        AnalysisFeedbackSubmitRequest req2 = validRequest("REVIEW_SUMMARY", null, "INACCURATE", "有改进空间");

        service.submitFeedback(1L, 10L, req1);
        service.submitFeedback(1L, 10L, req2);

        // 两次都应该是 insert
        verify(feedbackMapper, times(2)).insert(any(AnalysisFeedback.class));
    }

    // ============================================================
    // AC-3: 支持填写并保存具体问题说明
    // ============================================================

    @Test
    void shouldSaveContentWhenProvided() {
        AnalysisFeedbackSubmitRequest req = validRequest("COMPETITOR", 201L, "INACCURATE",
                "竞品对比结果中遗漏了附近一家同类型餐厅");
        when(feedbackMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        service.submitFeedback(1L, 10L, req);

        ArgumentCaptor<AnalysisFeedback> captor = ArgumentCaptor.forClass(AnalysisFeedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertEquals("竞品对比结果中遗漏了附近一家同类型餐厅", captor.getValue().getContent());
    }

    @Test
    void shouldAllowEmptyContent() {
        AnalysisFeedbackSubmitRequest req = validRequest("HIGHLIGHT", 301L, "ACCURATE", null);
        when(feedbackMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AnalysisFeedbackVO result = service.submitFeedback(1L, 10L, req);

        assertNull(result.getContent());
    }

    @Test
    void shouldRejectContentTooLong() {
        AnalysisFeedbackSubmitRequest req = validRequest("SENTIMENT", 101L, "ACCURATE",
                "a".repeat(2001));

        assertThrows(ApiException.class, () -> service.submitFeedback(1L, 10L, req));
    }

    // ============================================================
    // 参数校验
    // ============================================================

    @Test
    void shouldRejectMissingAnalysisType() {
        AnalysisFeedbackSubmitRequest req = new AnalysisFeedbackSubmitRequest();
        req.setFeedbackType("ACCURATE");

        ApiException ex = assertThrows(ApiException.class,
                () -> service.submitFeedback(1L, 10L, req));
        assertTrue(ex.getMessage().contains("analysisType"));
    }

    @Test
    void shouldRejectInvalidAnalysisType() {
        AnalysisFeedbackSubmitRequest req = validRequest("INVALID_TYPE", 101L, "ACCURATE", null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.submitFeedback(1L, 10L, req));
        assertTrue(ex.getMessage().contains("无效的分析类型"));
    }

    @Test
    void shouldRejectMissingFeedbackType() {
        AnalysisFeedbackSubmitRequest req = new AnalysisFeedbackSubmitRequest();
        req.setAnalysisType("SENTIMENT");

        ApiException ex = assertThrows(ApiException.class,
                () -> service.submitFeedback(1L, 10L, req));
        assertTrue(ex.getMessage().contains("feedbackType"));
    }

    @Test
    void shouldRejectInvalidFeedbackType() {
        AnalysisFeedbackSubmitRequest req = validRequest("SENTIMENT", 101L, "GOOD", null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.submitFeedback(1L, 10L, req));
        assertTrue(ex.getMessage().contains("无效的反馈类型"));
    }

    @Test
    void shouldAcceptAllSevenAnalysisTypes() {
        String[] types = {"SENTIMENT", "KEYWORD", "ISSUE_ATTRIBUTION", "COMPETITOR",
                "BUSINESS_SUGGESTION", "REVIEW_SUMMARY", "HIGHLIGHT"};
        when(feedbackMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        for (String type : types) {
            AnalysisFeedbackSubmitRequest req = validRequest(type, 101L, "ACCURATE", null);
            assertDoesNotThrow(() -> service.submitFeedback(1L, 10L, req));
        }
    }

    // ============================================================
    // AC-5: 商家用户查询其他店铺反馈时返回 403（由 Service 层 detail 方法校验）
    // ============================================================

    @Test
    void shouldRejectCrossMerchantDetailAccess() {
        AnalysisFeedback feedback = new AnalysisFeedback();
        feedback.setId(5L);
        feedback.setMerchantId(99L); // 属于商家 99
        when(feedbackMapper.selectById(5L)).thenReturn(feedback);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.getFeedbackDetail(5L, 1L)); // 商家 1 试图访问
        assertEquals("无权查看该反馈记录", ex.getMessage());
    }

    @Test
    void shouldAllowOwnMerchantDetailAccess() {
        AnalysisFeedback feedback = new AnalysisFeedback();
        feedback.setId(5L);
        feedback.setMerchantId(1L);
        feedback.setAnalysisType("SENTIMENT");
        feedback.setFeedbackType("ACCURATE");
        when(feedbackMapper.selectById(5L)).thenReturn(feedback);

        AnalysisFeedbackVO result = service.getFeedbackDetail(5L, 1L);
        assertEquals(5L, result.getId());
    }

    @Test
    void shouldReturn404ForNonExistentFeedback() {
        when(feedbackMapper.selectById(999L)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.getFeedbackDetail(999L, 1L));
        assertEquals("反馈记录不存在", ex.getMessage());
    }

    // ============================================================
    // AC-4: 管理员能够按 AI 功能和反馈类型汇总数量
    // ============================================================

    @Test
    void shouldAggregateStatisticsByType() {
        when(feedbackMapper.statisticsByAnalysisType()).thenReturn(List.of(
                Map.of("analysisType", "SENTIMENT", "totalCount", 10L,
                        "accurateCount", 8L, "inaccurateCount", 2L),
                Map.of("analysisType", "KEYWORD", "totalCount", 5L,
                        "accurateCount", 4L, "inaccurateCount", 1L)
        ));

        AnalysisFeedbackStatisticsVO stats = service.getStatistics();

        assertAll(
                () -> assertEquals(15L, stats.getTotalCount()),
                () -> assertEquals(12L, stats.getAccurateCount()),
                () -> assertEquals(3L, stats.getInaccurateCount()),
                () -> assertEquals(0.8, stats.getAccuracyRate(), 0.001),
                () -> assertEquals(2, stats.getByAnalysisType().size()),
                () -> assertEquals("情感分析", stats.getByAnalysisType().get(0).getAnalysisTypeText()),
                () -> assertEquals(0.8, stats.getByAnalysisType().get(0).getAccuracyRate(), 0.001)
        );
    }

    @Test
    void shouldReturnNullAccuracyRateWhenNoData() {
        when(feedbackMapper.statisticsByAnalysisType()).thenReturn(Collections.emptyList());

        AnalysisFeedbackStatisticsVO stats = service.getStatistics();

        assertEquals(0L, stats.getTotalCount());
        assertNull(stats.getAccuracyRate());
        assertTrue(stats.getByAnalysisType().isEmpty());
    }

    // ============================================================
    // 商家端查询
    // ============================================================

    @Test
    void shouldFilterMerchantFeedbackByAnalysisType() {
        AnalysisFeedback fb = feedback(1L, "SENTIMENT", "ACCURATE");
        Page<AnalysisFeedback> page = new Page<>(1, 10);
        page.setRecords(List.of(fb));
        page.setTotal(1);

        when(feedbackMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        PageResult<AnalysisFeedbackVO> result = service.getMerchantFeedback(
                1L, "SENTIMENT", null, 1, 10);

        assertEquals(1, result.getTotal());
        assertEquals("SENTIMENT", result.getRecords().get(0).getAnalysisType());
    }

    @Test
    void shouldFilterMerchantFeedbackByFeedbackType() {
        Page<AnalysisFeedback> page = new Page<>(1, 10);
        page.setRecords(List.of(feedback(1L, "KEYWORD", "INACCURATE")));
        page.setTotal(1);

        when(feedbackMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        PageResult<AnalysisFeedbackVO> result = service.getMerchantFeedback(
                1L, null, "INACCURATE", 1, 10);

        assertEquals("INACCURATE", result.getRecords().get(0).getFeedbackType());
    }

    // ============================================================
    // AC-6: 管理员能够从反馈列表查看对应分析记录和问题说明
    // ============================================================

    @Test
    void shouldIncludeMerchantNameAndUsernameInAdminList() {
        AnalysisFeedback fb = feedback(1L, "SENTIMENT", "ACCURATE");
        fb.setAnalysisId(101L);
        fb.setContent("分析很准确");
        fb.setCreatedBy(10L);
        Page<AnalysisFeedback> page = new Page<>(1, 10);
        page.setRecords(List.of(fb));
        page.setTotal(1);

        when(feedbackMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);
        when(merchantMapper.selectBatchIds(any())).thenReturn(List.of(merchant(1L, "川味小馆")));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(10L, "merchant01")));

        PageResult<AnalysisFeedbackVO> result = service.getAllFeedback(
                null, null, null, 1, 10);

        AnalysisFeedbackVO vo = result.getRecords().get(0);
        assertAll(
                () -> assertEquals("川味小馆", vo.getMerchantName()),
                () -> assertEquals("merchant01", vo.getCreatedByUsername()),
                () -> assertEquals(101L, vo.getAnalysisId()),
                () -> assertEquals("分析很准确", vo.getContent())
        );
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private AnalysisFeedbackSubmitRequest validRequest(
            String analysisType, Long analysisId, String feedbackType, String content) {
        AnalysisFeedbackSubmitRequest req = new AnalysisFeedbackSubmitRequest();
        req.setAnalysisType(analysisType);
        req.setAnalysisId(analysisId);
        req.setFeedbackType(feedbackType);
        req.setContent(content);
        return req;
    }

    private AnalysisFeedback feedback(Long merchantId, String analysisType, String feedbackType) {
        AnalysisFeedback fb = new AnalysisFeedback();
        fb.setId(1L);
        fb.setMerchantId(merchantId);
        fb.setAnalysisType(analysisType);
        fb.setAnalysisId(101L);
        fb.setFeedbackType(feedbackType);
        fb.setContent("测试反馈");
        fb.setCreatedBy(10L);
        return fb;
    }

    private Merchant merchant(Long id, String name) {
        Merchant m = new Merchant();
        m.setId(id);
        m.setName(name);
        return m;
    }

    private User user(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }
}
