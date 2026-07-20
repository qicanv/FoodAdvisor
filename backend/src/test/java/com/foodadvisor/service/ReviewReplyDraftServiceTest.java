package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.review.ReviewReplyDraftVO;
import com.foodadvisor.dto.review.ReviewReplyVO;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewReply;
import com.foodadvisor.entity.ReviewReplyDraft;
import com.foodadvisor.trace.AiTraceContext;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.mapper.ReviewReplyDraftMapper;
import com.foodadvisor.mapper.ReviewReplyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * 评价辅助回复草稿服务单元测试（EPIC-02 故事7）
 *
 * 使用 Spy 方式：spy 真实 service 并 mock 其依赖 mapper。
 * 关键策略：mock ReviewReplyDraftMapper（baseMapper）控制 findActiveDraft 的行为。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewReplyDraftServiceTest {

    private static final Long MEMBER_ID = 10L;
    private static final Long REVIEW_ID = 1L;
    private static final Long MERCHANT_ID = 5L;
    private static final Long DRAFT_ID = 100L;
    private static final Long REPLY_ID = 200L;
    private static final String AI_CONTENT = "感谢您的支持和认可，我们家的招牌拿铁确实很受欢迎！";
    private static final String AI_TRACE_ID = "reply-uuid-12345";
    private static final String MODEL_NAME = "deepseek-v4-pro";

    @Mock private ReviewMapper reviewMapper;
    @Mock private MerchantMapper merchantMapper;
    @Mock private ReviewReplyMapper replyMapper;
    @Mock private AIClientService aiClientService;
    @Mock private NotificationService notificationService;
    @Mock private ReviewReplyDraftMapper baseMapperMock;
    @Mock private AiRequestTraceService traceService;

    private ReviewReplyDraftService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = spy(new ReviewReplyDraftService(
                reviewMapper, merchantMapper, replyMapper,
                aiClientService, notificationService
        ));
        // 将 mock 的 baseMapper 注入到 ServiceImpl 中
        // 所有 ServiceImpl 的 CRUD 操作都通过 baseMapper
        doReturn(baseMapperMock).when(service).getBaseMapper();
    }

    // ==================================================================
    // generateDraft() 测试
    // ==================================================================

    @Test
    void generateDraftReviewNotFoundShouldThrow() {
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.generateDraft(MEMBER_ID, REVIEW_ID));

        assertEquals("REVIEW_NOT_FOUND", ex.getCode());
        verify(aiClientService, never())
                .generateReplyDraft(anyLong(), anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    void generateDraftMerchantNotFoundShouldThrow() {
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.generateDraft(MEMBER_ID, REVIEW_ID));

        assertEquals("MERCHANT_NOT_FOUND", ex.getCode());
    }

    @Test
    void generateDraftExistingDraftReturnsExisting() throws Exception {
        // 已有活跃草稿 → 直接返回，不调用 AI
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        mockGetOneReturns(existingDraft());

        ReviewReplyDraftVO result = service.generateDraft(MEMBER_ID, REVIEW_ID);

        assertNotNull(result);
        assertEquals(DRAFT_ID, result.getId());
        verify(aiClientService, never())
                .generateReplyDraft(anyLong(), anyLong(), anyString(), anyString(), anyInt());
    }

    @Test
    void generateDraftPositiveStrategyRating5() throws Exception {
        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        when(aiClientService.generateReplyDraft(
                eq(REVIEW_ID), eq(MERCHANT_ID), anyString(), eq("POSITIVE"), eq(5)))
                .thenReturn(aiSuccessResponse());
        when(baseMapperMock.insert(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyDraftVO result = service.generateDraft(MEMBER_ID, REVIEW_ID);

        assertEquals("POSITIVE", result.getStrategy());
        assertEquals(AI_CONTENT, result.getGeneratedContent());
        assertEquals("DRAFT", result.getStatus());
    }

    @Test
    void tracedReplyUsesRootContextAndPositivePromptVersion() throws Exception {
        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        AiTraceContext context = new AiTraceContext("trc-reply-test", "req-reply-test", null,
                MEMBER_ID, "REVIEW_REPLY_GENERATION");
        when(aiClientService.generateReplyDraft(eq(REVIEW_ID), eq(MERCHANT_ID), anyString(),
                eq("POSITIVE"), eq(5), eq(context))).thenReturn(aiSuccessResponse());
        when(baseMapperMock.insert(Mockito.<ReviewReplyDraft>any())).thenReturn(1);
        ReviewReplyDraftService traced = spy(new ReviewReplyDraftService(
                reviewMapper, merchantMapper, replyMapper, aiClientService, notificationService, traceService));
        doReturn(baseMapperMock).when(traced).getBaseMapper();

        ReviewReplyDraftVO result = traced.generateDraft(MEMBER_ID, REVIEW_ID, context);

        assertEquals("trc-reply-test", result.getAiTraceId());
        verify(aiClientService).generateReplyDraft(eq(REVIEW_ID), eq(MERCHANT_ID), anyString(),
                eq("POSITIVE"), eq(5), eq(context));
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(traceService).completeTrace(eq(context), eq("SUCCESS"), any(), any(), any(), any(),
                promptCaptor.capture());
        assertEquals("review-reply-positive:v1", promptCaptor.getValue());
    }

    @Test
    void tracedReplyUsesNegativePromptVersion() throws Exception {
        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(1));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        AiTraceContext context = new AiTraceContext("trc-reply-negative", "req-reply-negative", null,
                MEMBER_ID, "REVIEW_REPLY_GENERATION");
        when(aiClientService.generateReplyDraft(eq(REVIEW_ID), eq(MERCHANT_ID), anyString(),
                eq("NEGATIVE"), eq(1), eq(context))).thenReturn(aiSuccessResponse());
        when(baseMapperMock.insert(Mockito.<ReviewReplyDraft>any())).thenReturn(1);
        ReviewReplyDraftService traced = spy(new ReviewReplyDraftService(
                reviewMapper, merchantMapper, replyMapper, aiClientService, notificationService, traceService));
        doReturn(baseMapperMock).when(traced).getBaseMapper();

        traced.generateDraft(MEMBER_ID, REVIEW_ID, context);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(traceService).completeTrace(eq(context), eq("SUCCESS"), any(), any(), any(), any(),
                promptCaptor.capture());
        assertEquals("review-reply-negative:v1", promptCaptor.getValue());
    }

    @Test
    void generateDraftNegativeStrategyRating1() throws Exception {
        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(1));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        when(aiClientService.generateReplyDraft(
                eq(REVIEW_ID), eq(MERCHANT_ID), anyString(), eq("NEGATIVE"), eq(1)))
                .thenReturn(aiSuccessResponse());
        when(baseMapperMock.insert(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyDraftVO result = service.generateDraft(MEMBER_ID, REVIEW_ID);

        assertEquals("NEGATIVE", result.getStrategy());
    }

    @Test
    void generateDraftRating3DefaultsToPositive() throws Exception {
        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(3));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        when(aiClientService.generateReplyDraft(anyLong(), anyLong(), anyString(), eq("POSITIVE"), eq(3)))
                .thenReturn(aiSuccessResponse());
        when(baseMapperMock.insert(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyDraftVO result = service.generateDraft(MEMBER_ID, REVIEW_ID);
        assertEquals("POSITIVE", result.getStrategy());
    }

    @Test
    void generateDraftNullRatingDefaultsToPositive() throws Exception {
        Review r = review(5);
        r.setRating(null);

        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(r);
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        // null rating 传递到 AI 服务时为 null
        when(aiClientService.generateReplyDraft(anyLong(), anyLong(), anyString(), eq("POSITIVE"), isNull()))
                .thenReturn(aiSuccessResponse());
        when(baseMapperMock.insert(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyDraftVO result = service.generateDraft(MEMBER_ID, REVIEW_ID);
        assertEquals("POSITIVE", result.getStrategy());
    }

    @Test
    void generateDraftAiFailureShouldThrow() {
        // 验收准则7：模型调用失败时显示明确提示且不覆盖已有草稿
        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        when(aiClientService.generateReplyDraft(anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.generateDraft(MEMBER_ID, REVIEW_ID));

        assertEquals("AI_REPLY_GENERATION_FAILED", ex.getCode());
        assertTrue(ex.getMessage().contains("Connection refused"));
        verify(baseMapperMock, never()).insert(ArgumentMatchers.<ReviewReplyDraft>any());
    }

    @Test
    void generateDraftSaveFailureShouldThrow() throws Exception {
        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        when(aiClientService.generateReplyDraft(anyLong(), anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(aiSuccessResponse());
        when(baseMapperMock.insert(Mockito.<ReviewReplyDraft>any())).thenReturn(0);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.generateDraft(MEMBER_ID, REVIEW_ID));

        assertEquals("DRAFT_SAVE_FAILED", ex.getCode());
    }

    // ==================================================================
    // editDraft() 测试
    // ==================================================================

    @Test
    void editDraftReviewNotFoundShouldThrow() {
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.editDraft(MEMBER_ID, REVIEW_ID, "修改"));

        assertEquals("REVIEW_NOT_FOUND", ex.getCode());
    }

    @Test
    void editDraftDraftNotFoundShouldThrow() {
        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.editDraft(MEMBER_ID, REVIEW_ID, "修改"));

        assertEquals("DRAFT_NOT_FOUND", ex.getCode());
    }

    @Test
    void editDraftEmptyContentShouldThrow() {
        mockGetOneReturns(existingDraft());
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.editDraft(MEMBER_ID, REVIEW_ID, "   "));

        assertEquals("DRAFT_CONTENT_EMPTY", ex.getCode());
    }

    @Test
    void editDraftSavesEditedContent() {
        String edited = "感谢反馈！已改进。";
        mockGetOneReturns(existingDraft());
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(baseMapperMock.updateById(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyDraftVO result = service.editDraft(MEMBER_ID, REVIEW_ID, edited);

        assertEquals(edited, result.getEditedContent());
        assertEquals(edited, result.getEffectiveContent());
    }

    @Test
    void editDraftSanitizesPhoneNumber() {
        // 验收准则5：不含完整联系方式
        String content = "联系 13812345678 谢谢";
        mockGetOneReturns(existingDraft());
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(baseMapperMock.updateById(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyDraftVO result = service.editDraft(MEMBER_ID, REVIEW_ID, content);

        assertFalse(result.getEditedContent().contains("13812345678"));
        assertTrue(result.getEditedContent().contains("[已隐藏联系方式]"));
    }

    @Test
    void editDraftSanitizesEmail() {
        String content = "邮箱 test@example.com 联系";
        mockGetOneReturns(existingDraft());
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(baseMapperMock.updateById(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyDraftVO result = service.editDraft(MEMBER_ID, REVIEW_ID, content);

        assertFalse(result.getEditedContent().contains("test@example.com"));
        assertTrue(result.getEditedContent().contains("[已隐藏邮箱]"));
    }

    @Test
    void editDraftForbiddenForDifferentMerchant() {
        mockGetOneReturns(existingDraft());
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5, 999L));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.editDraft(MEMBER_ID, REVIEW_ID, "edited"));

        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void editDraftUpdateFailureShouldThrow() {
        mockGetOneReturns(existingDraft());
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(baseMapperMock.updateById(Mockito.<ReviewReplyDraft>any())).thenReturn(0);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.editDraft(MEMBER_ID, REVIEW_ID, "valid content"));

        assertEquals("DRAFT_UPDATE_FAILED", ex.getCode());
    }

    // ==================================================================
    // publishDraft() 测试
    // ==================================================================

    @Test
    void publishDraftReviewNotFoundShouldThrow() {
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.publishDraft(MEMBER_ID, REVIEW_ID));

        assertEquals("REVIEW_NOT_FOUND", ex.getCode());
    }

    @Test
    void publishDraftDraftNotFoundShouldThrow() {
        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.publishDraft(MEMBER_ID, REVIEW_ID));

        assertEquals("DRAFT_NOT_FOUND", ex.getCode());
    }

    @Test
    void publishDraftCreatesNewReply() {
        mockGetOneReturns(existingDraft());
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        when(replyMapper.selectOne(any())).thenReturn(null);
        when(replyMapper.insert(any(ReviewReply.class))).thenReturn(1);
        when(baseMapperMock.updateById(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyVO result = service.publishDraft(MEMBER_ID, REVIEW_ID);

        assertNotNull(result);
        assertEquals(MERCHANT_ID, result.getMerchantId());
        assertEquals(AI_CONTENT, result.getReplyContent());
        assertEquals("VISIBLE", result.getStatus());
        verify(replyMapper, times(1)).insert(any(ReviewReply.class));
        verify(replyMapper, never()).updateById(any(ReviewReply.class));
        verify(notificationService, times(1))
                .createReplyNotification(eq(REVIEW_ID), eq(MERCHANT_ID), anyString(), anyString());
    }

    @Test
    void publishDraftUpdatesExistingReply() {
        mockGetOneReturns(existingDraft());
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());

        ReviewReply existingReply = new ReviewReply();
        existingReply.setId(REPLY_ID);
        existingReply.setReviewId(REVIEW_ID);
        existingReply.setMerchantId(MERCHANT_ID);
        when(replyMapper.selectOne(any())).thenReturn(existingReply);
        when(replyMapper.updateById(any(ReviewReply.class))).thenReturn(1);
        when(baseMapperMock.updateById(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyVO result = service.publishDraft(MEMBER_ID, REVIEW_ID);

        assertEquals(REPLY_ID, result.getId());
        verify(replyMapper, times(1)).updateById(any(ReviewReply.class));
        verify(replyMapper, never()).insert(any(ReviewReply.class));
    }

    @Test
    void publishDraftUsesEditedContentWhenAvailable() {
        String edited = "改进后的回复";
        ReviewReplyDraft draft = existingDraft();
        draft.setEditedContent(edited);

        mockGetOneReturns(draft);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        when(replyMapper.selectOne(any())).thenReturn(null);
        when(replyMapper.insert(any(ReviewReply.class))).thenReturn(1);
        when(baseMapperMock.updateById(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyVO result = service.publishDraft(MEMBER_ID, REVIEW_ID);

        assertEquals(edited, result.getReplyContent());
        assertNotEquals(AI_CONTENT, result.getReplyContent());
    }

    @Test
    void publishDraftSanitizesFinalContent() {
        ReviewReplyDraft draft = existingDraft();
        draft.setEditedContent("咨询 13900139000");

        mockGetOneReturns(draft);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(merchantMapper.selectById(MERCHANT_ID)).thenReturn(merchant());
        when(replyMapper.selectOne(any())).thenReturn(null);
        when(replyMapper.insert(any(ReviewReply.class))).thenReturn(1);
        when(baseMapperMock.updateById(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        ReviewReplyVO result = service.publishDraft(MEMBER_ID, REVIEW_ID);

        assertFalse(result.getReplyContent().contains("13900139000"));
    }

    // ==================================================================
    // getDraft() 测试
    // ==================================================================

    @Test
    void getDraftNoDraftReturnsNull() {
        mockGetOneReturns(null);

        assertNull(service.getDraft(REVIEW_ID));
    }

    @Test
    void getDraftHasDraftReturnsVO() {
        mockGetOneReturns(existingDraft());

        ReviewReplyDraftVO result = service.getDraft(REVIEW_ID);

        assertNotNull(result);
        assertEquals(DRAFT_ID, result.getId());
        assertEquals(AI_CONTENT, result.getGeneratedContent());
        assertEquals("DRAFT", result.getStatus());
    }

    @Test
    void getDraftEffectiveContentPrefersEdited() {
        ReviewReplyDraft draft = existingDraft();
        draft.setEditedContent("编辑后内容");
        mockGetOneReturns(draft);

        assertEquals("编辑后内容", service.getDraft(REVIEW_ID).getEffectiveContent());
    }

    @Test
    void getDraftEffectiveContentFallsBackToGenerated() {
        ReviewReplyDraft draft = existingDraft();
        draft.setEditedContent(null);
        mockGetOneReturns(draft);

        assertEquals(AI_CONTENT, service.getDraft(REVIEW_ID).getEffectiveContent());
    }

    // ==================================================================
    // discardDraft() 测试
    // ==================================================================

    @Test
    void discardDraftReviewNotFoundShouldThrow() {
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.discardDraft(MEMBER_ID, REVIEW_ID));

        assertEquals("REVIEW_NOT_FOUND", ex.getCode());
    }

    @Test
    void discardDraftNotFoundShouldThrow() {
        mockGetOneReturns(null);
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.discardDraft(MEMBER_ID, REVIEW_ID));

        assertEquals("DRAFT_NOT_FOUND", ex.getCode());
    }

    @Test
    void discardDraftMarksAsDiscarded() {
        mockGetOneReturns(existingDraft());
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(baseMapperMock.updateById(Mockito.<ReviewReplyDraft>any())).thenReturn(1);

        assertDoesNotThrow(() -> service.discardDraft(MEMBER_ID, REVIEW_ID));

        ArgumentCaptor<ReviewReplyDraft> captor = ArgumentCaptor.forClass(ReviewReplyDraft.class);
        verify(baseMapperMock).updateById(captor.capture());
        assertEquals("DISCARDED", captor.getValue().getStatus());
    }

    @Test
    void discardDraftUpdateFailureShouldThrow() {
        mockGetOneReturns(existingDraft());
        when(reviewMapper.selectById(REVIEW_ID)).thenReturn(review(5));
        when(baseMapperMock.updateById(Mockito.<ReviewReplyDraft>any())).thenReturn(0);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.discardDraft(MEMBER_ID, REVIEW_ID));

        assertEquals("DRAFT_DISCARD_FAILED", ex.getCode());
    }

    // ==================================================================
    // ReviewReplyDraftVO.from() 测试
    // ==================================================================

    @Test
    void voFromNullReturnsNull() {
        assertNull(ReviewReplyDraftVO.from(null));
    }

    @Test
    void voFromEntityMapsAllFields() {
        ReviewReplyDraft entity = existingDraft();
        entity.setEditedContent("已编辑");
        entity.setPublishedAt(OffsetDateTime.now());

        ReviewReplyDraftVO vo = ReviewReplyDraftVO.from(entity);
        assertNotNull(vo);
        assertEquals(entity.getId(), vo.getId());
        assertEquals(entity.getGeneratedContent(), vo.getGeneratedContent());
        assertEquals(entity.getEditedContent(), vo.getEditedContent());
        assertEquals(entity.getStrategy(), vo.getStrategy());
    }

    // ==================================================================
    // 辅助方法
    // ==================================================================

    /** Mock findActiveDraft（内部调用 this.getOne()）的返回值 */
    private void mockGetOneReturns(ReviewReplyDraft draft) {
        doReturn(draft).when(service).getOne(any());
    }

    private Review review(int rating) {
        return review(rating, MERCHANT_ID);
    }

    private Review review(int rating, Long merchantId) {
        Review r = new Review();
        r.setId(REVIEW_ID);
        r.setMerchantId(merchantId);
        r.setUserId(1L);
        r.setContent("招牌拿铁很好喝，服务也不错！");
        r.setRating(BigDecimal.valueOf(rating));
        r.setStatus("PUBLISHED");
        r.setModerationStatus("APPROVED");
        return r;
    }

    private Merchant merchant() {
        Merchant m = new Merchant();
        m.setId(MERCHANT_ID);
        m.setName("测试商家");
        m.setPlatformStatus("ACTIVE");
        m.setOperationStatus("OPERATING");
        return m;
    }

    private ReviewReplyDraft existingDraft() {
        ReviewReplyDraft d = new ReviewReplyDraft();
        d.setId(DRAFT_ID);
        d.setReviewId(REVIEW_ID);
        d.setMerchantId(MERCHANT_ID);
        d.setGeneratedContent(AI_CONTENT);
        d.setStrategy("POSITIVE");
        d.setStatus("DRAFT");
        d.setGeneratedAt(OffsetDateTime.now());
        d.setAiTraceId(AI_TRACE_ID);
        d.setModelName(MODEL_NAME);
        return d;
    }

    private JsonNode aiSuccessResponse() throws Exception {
        return objectMapper.readTree(
                "{\"reviewId\":" + REVIEW_ID
                + ",\"replyContent\":\"" + AI_CONTENT + "\""
                + ",\"strategy\":\"POSITIVE\""
                + ",\"businessTraceId\":\"" + AI_TRACE_ID + "\""
                + ",\"modelName\":\"" + MODEL_NAME + "\""
                + ",\"status\":\"SUCCESS\"}"
        );
    }
}
