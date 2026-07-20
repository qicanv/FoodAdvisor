package com.foodadvisor.controller;

import com.foodadvisor.dto.review.ReviewReplyDraftVO;
import com.foodadvisor.service.AiRequestTraceService;
import com.foodadvisor.service.AIClientService;
import com.foodadvisor.service.ReviewReplyDraftService;
import com.foodadvisor.trace.AiTraceContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewReplyTraceControllerTest {
    @Test
    void replyGenerationCreatesOneRootAndReturnsIt() {
        ReviewServiceFixture fixture = new ReviewServiceFixture();
        AiTraceContext context = new AiTraceContext("trc-reply", "req-reply", null, 10L,
                "REVIEW_REPLY_GENERATION");
        when(fixture.trace.startTrace(any(), any(), eq(10L), eq("REVIEW_REPLY_GENERATION")))
                .thenReturn(context);
        ReviewReplyDraftVO draft = new ReviewReplyDraftVO();
        draft.setId(77L);
        draft.setReviewId(5L);
        draft.setMerchantId(10L);
        draft.setStatus("DRAFT");
        draft.setStrategy("POSITIVE");
        when(fixture.service.generateDraft(10L, 5L, context)).thenReturn(draft);

        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ReviewReplyDraftVO response = fixture.controller
                .generateReplyDraft(5L, 10L, servletResponse).data();

        assertEquals("trc-reply", response.getTraceId());
        assertEquals("trc-reply", response.getAiTraceId());
        assertEquals("trc-reply", servletResponse.getHeader("X-Trace-Id"));
        verify(fixture.trace, times(1)).startTrace(any(), any(), eq(10L), eq("REVIEW_REPLY_GENERATION"));
        verify(fixture.service).generateDraft(10L, 5L, context);
    }

    private static class ReviewServiceFixture {
        final ReviewReplyDraftService service = mock(ReviewReplyDraftService.class);
        final AiRequestTraceService trace = mock(AiRequestTraceService.class);
        final ReviewController controller = new ReviewController(
                mock(com.foodadvisor.service.ReviewService.class),
                mock(AIClientService.class),
                mock(com.foodadvisor.mapper.ReviewTagMapper.class),
                mock(com.foodadvisor.mapper.ReviewIssueCategoryMapper.class),
                service, trace);
    }
}
