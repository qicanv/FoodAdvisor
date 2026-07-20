package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.ReviewAnalysisResultVO;
import com.foodadvisor.dto.ReviewBatchAnalysisResultVO;
import com.foodadvisor.entity.AiRequestTraceStage;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewAnalysis;
import com.foodadvisor.mapper.ReviewIssueCategoryMapper;
import com.foodadvisor.mapper.ReviewTagMapper;
import com.foodadvisor.service.AIClientService;
import com.foodadvisor.service.AiRequestTraceService;
import com.foodadvisor.service.ReviewReplyDraftService;
import com.foodadvisor.service.ReviewService;
import com.foodadvisor.trace.AiTraceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewAnalysisTraceControllerTest {
    @Mock private ReviewService reviewService;
    @Mock private AIClientService aiClientService;
    @Mock private ReviewTagMapper reviewTagMapper;
    @Mock private ReviewIssueCategoryMapper issueCategoryMapper;
    @Mock private ReviewReplyDraftService replyDraftService;
    @Mock private AiRequestTraceService traceService;

    private ReviewController controller;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong stageIds = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        controller = new ReviewController(reviewService, aiClientService, reviewTagMapper,
                issueCategoryMapper, replyDraftService, traceService);
        when(traceService.startStage(any(AiTraceContext.class), anyString(), any()))
                .thenAnswer(invocation -> {
                    AiRequestTraceStage stage = new AiRequestTraceStage();
                    stage.setId(stageIds.getAndIncrement());
                    return stage;
                });
    }

    @Test
    void singleAnalysisUsesOneRootAndReturnsSameTraceId() throws Exception {
        Review review = review(1L);
        ReviewAnalysis persisted = analysis(101L, 1L);
        AiTraceContext context = new AiTraceContext("trc-review-one", "req-review-one",
                null, null, "REVIEW_ANALYSIS");
        when(reviewService.getById(1L)).thenReturn(review);
        when(reviewService.getAnalysis(1L)).thenReturn(persisted);
        when(traceService.startTrace(any(), any(), any(), eq("REVIEW_ANALYSIS"))).thenReturn(context);
        when(aiClientService.analyzeReview(eq(1L), eq(10L), anyString(), eq(1), eq(context)))
                .thenReturn(successResult(1L));

        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ReviewAnalysisResultVO response = controller.analyze(1L, servletResponse).data();

        assertEquals("trc-review-one", response.getTraceId());
        assertEquals("trc-review-one", response.getBusinessTraceId());
        assertEquals(101L, response.getReviewAnalysisId());
        assertEquals("trc-review-one", servletResponse.getHeader("X-Trace-Id"));
        verify(traceService, times(1)).startTrace(any(), any(), any(), eq("REVIEW_ANALYSIS"));
        verify(aiClientService).analyzeReview(eq(1L), eq(10L), anyString(), eq(1), eq(context));
        verify(traceService).completeTrace(eq(context), eq("SUCCESS"), any(), any(), any(), any(), any());

        ArgumentCaptor<AiTraceContext> contexts = ArgumentCaptor.forClass(AiTraceContext.class);
        verify(traceService, atLeast(5)).startStage(contexts.capture(), anyString(), any());
        assertTrue(contexts.getAllValues().stream()
                .allMatch(value -> "trc-review-one".equals(value.traceId())));
    }

    @Test
    void batchUsesOneParentRootAndContinuesAfterChildFailure() throws Exception {
        Review first = review(1L);
        Review second = review(2L);
        Review third = review(3L);
        Page<Review> page = new Page<>();
        page.setRecords(List.of(first, second, third));
        AiTraceContext context = new AiTraceContext("trc-review-batch", "req-review-batch",
                null, null, "BATCH_REVIEW_ANALYSIS");
        when(reviewService.listByMerchant(10L, 1, 100)).thenReturn(page);
        when(reviewService.getAnalysis(1L)).thenReturn(null, analysis(101L, 1L));
        when(reviewService.getAnalysis(2L)).thenReturn(null);
        when(reviewService.getAnalysis(3L)).thenReturn(null, analysis(103L, 3L));
        when(traceService.startTrace(any(), any(), any(), eq("BATCH_REVIEW_ANALYSIS"))).thenReturn(context);
        when(aiClientService.analyzeReview(anyLong(), eq(10L), anyString(), eq(1), eq(context)))
                .thenAnswer(invocation -> {
                    if (invocation.getArgument(0, Long.class) == 2L) {
                        throw new RuntimeException("model unavailable");
                    }
                    return successResult(invocation.getArgument(0, Long.class));
                });

        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ReviewBatchAnalysisResultVO response = controller.batchAnalyze(10L, servletResponse).data();

        assertEquals("trc-review-batch", response.getTraceId());
        assertEquals(3, response.getRequestedCount());
        assertEquals(2, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(0, response.getSkippedCount());
        assertEquals(List.of(101L, 103L), response.getAnalysisIds());
        assertEquals("trc-review-batch", servletResponse.getHeader("X-Trace-Id"));
        verify(traceService, times(1)).startTrace(any(), any(), any(), eq("BATCH_REVIEW_ANALYSIS"));
        verify(traceService).completeTrace(eq(context), eq("FALLBACK"), any(), any(), any(), any(), any());
        verify(traceService).failRunningStagesSafely(eq(context), anyString(), anyString());
        verify(aiClientService, never()).analyzeReview(anyLong(), anyLong(), anyString(), anyInt());

        ArgumentCaptor<AiTraceContext> contexts = ArgumentCaptor.forClass(AiTraceContext.class);
        verify(traceService, atLeast(9)).startStage(contexts.capture(), anyString(), any());
        assertTrue(contexts.getAllValues().stream()
                .allMatch(value -> "trc-review-batch".equals(value.traceId())));
    }

    private Review review(Long id) {
        Review review = new Review();
        review.setId(id);
        review.setMerchantId(10L);
        review.setCurrentVersion(1);
        review.setContent("Short review " + id);
        return review;
    }

    private ReviewAnalysis analysis(Long id, Long reviewId) {
        ReviewAnalysis analysis = new ReviewAnalysis();
        analysis.setId(id);
        analysis.setReviewId(reviewId);
        return analysis;
    }

    private JsonNode successResult(Long reviewId) throws Exception {
        return objectMapper.readTree("""
                {"reviewId": %d, "merchantId": 10, "reviewVersion": 1,
                 "analysisVersion": 1, "sentiment": "POSITIVE", "confidence": 0.9,
                 "lowConfidence": false, "keywords": [], "aspects": [], "tags": [],
                 "issueCategories": [], "modelName": "test-model", "modelVersion": "v1",
                 "promptVersion": "review-analysis:v1", "businessTraceId": "python-local",
                 "status": "SUCCESS"}
                """.formatted(reviewId));
    }
}
