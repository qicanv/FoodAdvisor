package com.foodadvisor.controller;

import com.foodadvisor.dto.review.EditReplyDraftRequest;
import com.foodadvisor.dto.review.ReviewReplyDraftVO;
import com.foodadvisor.dto.review.ReviewReplyVO;
import com.foodadvisor.dto.review.ReviewSubmitRequest;
import com.foodadvisor.dto.review.ReviewSubmitResponse;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.mapper.ReviewIssueCategoryMapper;
import com.foodadvisor.mapper.ReviewTagMapper;
import com.foodadvisor.service.AIClientService;
import com.foodadvisor.service.ReviewService;
import com.foodadvisor.service.ReviewReplyDraftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock
    private ReviewService reviewService;
    @Mock
    private AIClientService aiClientService;
    @Mock
    private ReviewTagMapper reviewTagMapper;
    @Mock
    private ReviewIssueCategoryMapper issueCategoryMapper;
    @Mock
    private ReviewReplyDraftService replyDraftService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReviewController controller = new ReviewController(
                reviewService,
                aiClientService,
                reviewTagMapper,
                issueCategoryMapper,
                replyDraftService
        );
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldSubmitReviewWithoutImagesUsingAuthenticatedUserId()
            throws Exception {
        when(reviewService.submitOriginalReview(
                eq(7L),
                eq(11L),
                any(ReviewSubmitRequest.class),
                anyList()
        )).thenReturn(response(101L));

        mockMvc.perform(multipart("/api/reviews/merchants/{merchantId}", 11L)
                        .param("content", "Fresh noodles tasted excellent")
                        .param("rating", "5")
                        .param("tasteRating", "5")
                        .param("environmentRating", "4")
                        .param("serviceRating", "5")
                        .param("averageSpend", "66.50")
                        .param("consumptionDate", "2026-07-18")
                        .param("tags", "NOODLES")
                        .param("keepImageIds", "23")
                        .requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(101));

        ArgumentCaptor<ReviewSubmitRequest> requestCaptor =
                ArgumentCaptor.forClass(ReviewSubmitRequest.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartFile>> imagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(reviewService).submitOriginalReview(
                eq(7L),
                eq(11L),
                requestCaptor.capture(),
                imagesCaptor.capture()
        );

        ReviewSubmitRequest request = requestCaptor.getValue();
        assertAll(
                () -> assertEquals(
                        "Fresh noodles tasted excellent",
                        request.getContent()
                ),
                () -> assertEquals(5, request.getRating()),
                () -> assertEquals(5, request.getTasteRating()),
                () -> assertEquals(4, request.getEnvironmentRating()),
                () -> assertEquals(5, request.getServiceRating()),
                () -> assertEquals(
                        new BigDecimal("66.50"),
                        request.getAverageSpend()
                ),
                () -> assertEquals(
                        LocalDate.of(2026, 7, 18),
                        request.getConsumptionDate()
                ),
                () -> assertEquals(List.of("NOODLES"), request.getTags()),
                () -> assertEquals(List.of(23L), request.getKeepImageIds()),
                () -> assertEquals(List.of(), imagesCaptor.getValue())
        );
    }

    @Test
    void shouldPassImagesToService() throws Exception {
        when(reviewService.submitOriginalReview(
                eq(7L),
                eq(11L),
                any(ReviewSubmitRequest.class),
                anyList()
        )).thenReturn(response(102L));
        MockMultipartFile image = new MockMultipartFile(
                "images",
                "meal.png",
                "image/png",
                "image-data".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/reviews/merchants/{merchantId}", 11L)
                        .file(image)
                        .param("content", "Fresh noodles tasted excellent")
                        .param("rating", "5")
                        .requestAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(102));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartFile>> imagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(reviewService).submitOriginalReview(
                eq(7L),
                eq(11L),
                any(ReviewSubmitRequest.class),
                imagesCaptor.capture()
        );
        assertAll(
                () -> assertEquals(1, imagesCaptor.getValue().size()),
                () -> assertEquals(
                        "meal.png",
                        imagesCaptor.getValue().get(0).getOriginalFilename()
                )
        );
    }

    @Test
    void shouldRejectRequestWithoutAuthenticatedUserId() throws Exception {
        mockMvc.perform(multipart("/api/reviews/merchants/{merchantId}", 11L)
                        .param("content", "Fresh noodles tasted excellent")
                        .param("rating", "5")
                        .header("X-User-Id", "999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(reviewService, never()).submitOriginalReview(
                any(),
                any(),
                any(),
                anyList()
        );
    }

    @Test
    void shouldIgnoreForgedUserIdHeader() throws Exception {
        when(reviewService.submitOriginalReview(
                eq(7L),
                eq(11L),
                any(ReviewSubmitRequest.class),
                anyList()
        )).thenReturn(response(103L));

        mockMvc.perform(multipart("/api/reviews/merchants/{merchantId}", 11L)
                        .param("content", "Fresh noodles tasted excellent")
                        .param("rating", "5")
                        .header("X-User-Id", "999")
                        .requestAttr("userId", 7))
                .andExpect(status().isOk());

        verify(reviewService).submitOriginalReview(
                eq(7L),
                eq(11L),
                any(ReviewSubmitRequest.class),
                anyList()
        );
    }

    @Test
    void shouldReturnUnifiedErrorWhenServiceRejectsReview()
            throws Exception {
        when(reviewService.submitOriginalReview(
                eq(7L),
                eq(11L),
                any(ReviewSubmitRequest.class),
                anyList()
        )).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST,
                "MERCHANT_NOT_REVIEWABLE",
                "Merchant cannot receive reviews"
        ));

        mockMvc.perform(multipart("/api/reviews/merchants/{merchantId}", 11L)
                        .param("content", "Fresh noodles tasted excellent")
                        .param("rating", "5")
                        .requestAttr("userId", 7L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MERCHANT_NOT_REVIEWABLE"))
                .andExpect(jsonPath("$.message")
                        .value("Merchant cannot receive reviews"));
    }

    @Test
    void shouldNotExposeDropConstraintRoute() {
        assertFalse(hasPostMapping("/drop-constraint"));
    }

    @Test
    void shouldNotExposeReloadSeedRoute() {
        assertFalse(hasPostMapping("/reload-seed"));
    }

    // ==================== 评价辅助回复（EPIC-02 故事7）Controller 测试 ====================

    @Test
    void shouldGenerateReplyDraft() throws Exception {
        ReviewReplyDraftVO vo = replyDraftVO();
        when(replyDraftService.generateDraft(eq(10L), eq(1L))).thenReturn(vo);

        mockMvc.perform(post("/api/reviews/{reviewId}/reply-draft/generate", 1L)
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.generatedContent")
                        .value("感谢您的支持和认可！"))
                .andExpect(jsonPath("$.data.strategy").value("POSITIVE"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(replyDraftService, times(1)).generateDraft(10L, 1L);
    }

    @Test
    void generateReplyDraftShouldReturnErrorWhenServiceThrows() throws Exception {
        when(replyDraftService.generateDraft(eq(10L), eq(1L)))
                .thenThrow(new ApiException(
                        HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "评价不存在"));

        mockMvc.perform(post("/api/reviews/{reviewId}/reply-draft/generate", 1L)
                        .header("X-User-Id", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("评价不存在"));
    }

    @Test
    void generateReplyDraftShouldReturnErrorWhenAiFails() throws Exception {
        // 验收准则7：模型调用失败时显示明确提示
        when(replyDraftService.generateDraft(eq(10L), eq(1L)))
                .thenThrow(new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI_REPLY_GENERATION_FAILED",
                        "AI 回复生成失败：Connection refused"));

        mockMvc.perform(post("/api/reviews/{reviewId}/reply-draft/generate", 1L)
                        .header("X-User-Id", "10"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_REPLY_GENERATION_FAILED"));
    }

    @Test
    void shouldEditReplyDraft() throws Exception {
        ReviewReplyDraftVO vo = replyDraftVO();
        vo.setEditedContent("修改后的回复内容");
        when(replyDraftService.editDraft(eq(10L), eq(1L), eq("修改后的回复内容")))
                .thenReturn(vo);

        mockMvc.perform(put("/api/reviews/{reviewId}/reply-draft", 1L)
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"editedContent\":\"修改后的回复内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.editedContent").value("修改后的回复内容"));

        verify(replyDraftService, times(1))
                .editDraft(10L, 1L, "修改后的回复内容");
    }

    @Test
    void editReplyDraftShouldRejectEmptyContent() throws Exception {
        mockMvc.perform(put("/api/reviews/{reviewId}/reply-draft", 1L)
                        .header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"editedContent\":\"   \"}"))
                .andExpect(status().isOk())  // ApiResponse 封装，HTTP 200
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("回复内容不能为空"));

        // 验证 service 没有被调用
        verify(replyDraftService, never()).editDraft(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldPublishReplyDraft() throws Exception {
        ReviewReplyVO replyVO = replyVO();
        when(replyDraftService.publishDraft(eq(10L), eq(1L))).thenReturn(replyVO);

        mockMvc.perform(post("/api/reviews/{reviewId}/reply-draft/publish", 1L)
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(200))
                .andExpect(jsonPath("$.data.replyContent")
                        .value("感谢您的支持和认可！"))
                .andExpect(jsonPath("$.data.status").value("VISIBLE"))
                .andExpect(jsonPath("$.data.merchantName").value("测试商家"));

        // 验收准则4：未点击确认时回复不进入已发布状态
        // publishDraft 被调用后才会有正式回复
        verify(replyDraftService, times(1)).publishDraft(10L, 1L);
    }

    @Test
    void publishReplyDraftShouldReturnErrorWhenDraftNotFound() throws Exception {
        when(replyDraftService.publishDraft(eq(10L), eq(1L)))
                .thenThrow(new ApiException(
                        HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "没有找到活跃的回复草稿"));

        mockMvc.perform(post("/api/reviews/{reviewId}/reply-draft/publish", 1L)
                        .header("X-User-Id", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRAFT_NOT_FOUND"));
    }

    @Test
    void shouldGetReplyDraft() throws Exception {
        ReviewReplyDraftVO vo = replyDraftVO();
        when(replyDraftService.getDraft(1L)).thenReturn(vo);

        mockMvc.perform(get("/api/reviews/{reviewId}/reply-draft", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(replyDraftService, times(1)).getDraft(1L);
    }

    @Test
    void getReplyDraftShouldReturnNullWhenNoDraft() throws Exception {
        when(replyDraftService.getDraft(1L)).thenReturn(null);

        mockMvc.perform(get("/api/reviews/{reviewId}/reply-draft", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldDiscardReplyDraft() throws Exception {
        doNothing().when(replyDraftService).discardDraft(10L, 1L);

        mockMvc.perform(delete("/api/reviews/{reviewId}/reply-draft", 1L)
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("草稿已丢弃"));

        verify(replyDraftService, times(1)).discardDraft(10L, 1L);
    }

    @Test
    void discardReplyDraftShouldReturnErrorWhenDraftNotFound() throws Exception {
        doThrow(new ApiException(
                HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "没有找到活跃的回复草稿"))
                .when(replyDraftService).discardDraft(10L, 1L);

        mockMvc.perform(delete("/api/reviews/{reviewId}/reply-draft", 1L)
                        .header("X-User-Id", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRAFT_NOT_FOUND"));
    }

    // ==================== 评价辅助回复 测试结束 ====================

    private boolean hasPostMapping(String path) {
        return Arrays.stream(ReviewController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch(path::equals);
    }

    private ReviewSubmitResponse response(Long id) {
        ReviewSubmitResponse response = new ReviewSubmitResponse();
        response.setId(id);
        response.setUserId(7L);
        response.setMerchantId(11L);
        response.setStatus("PUBLISHED");
        response.setModerationStatus("APPROVED");
        response.setRiskLevel("LOW");
        return response;
    }

    private ReviewReplyDraftVO replyDraftVO() {
        ReviewReplyDraftVO vo = new ReviewReplyDraftVO();
        vo.setId(100L);
        vo.setReviewId(1L);
        vo.setMerchantId(5L);
        vo.setGeneratedContent("感谢您的支持和认可！");
        vo.setStrategy("POSITIVE");
        vo.setStatus("DRAFT");
        vo.setAiTraceId("reply-uuid-12345");
        vo.setModelName("deepseek-v4-pro");
        return vo;
    }

    private ReviewReplyVO replyVO() {
        ReviewReplyVO vo = new ReviewReplyVO();
        vo.setId(200L);
        vo.setReviewId(1L);
        vo.setMerchantId(5L);
        vo.setReplyContent("感谢您的支持和认可！");
        vo.setStatus("VISIBLE");
        vo.setMerchantName("测试商家");
        return vo;
    }
}
