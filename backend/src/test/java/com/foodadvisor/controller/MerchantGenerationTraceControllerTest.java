package com.foodadvisor.controller;

import com.foodadvisor.dto.highlight.MerchantHighlightVO;
import com.foodadvisor.dto.summary.MerchantReviewSummaryVO;
import com.foodadvisor.service.AiRequestTraceService;
import com.foodadvisor.service.MerchantHighlightService;
import com.foodadvisor.service.MerchantReviewSummaryService;
import com.foodadvisor.trace.AiTraceContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantGenerationTraceControllerTest {
    @Test
    void summaryGenerationReturnsTheRootTraceId() {
        MerchantReviewSummaryService service = mock(MerchantReviewSummaryService.class);
        AiRequestTraceService traceService = mock(AiRequestTraceService.class);
        MerchantReviewSummaryController controller = new MerchantReviewSummaryController(service, traceService);
        AiTraceContext context = new AiTraceContext("trc-summary", "req-summary", null, null,
                "MERCHANT_REVIEW_SUMMARY");
        MerchantReviewSummaryVO summary = new MerchantReviewSummaryVO();
        summary.setSummaryId(11L);
        summary.setMerchantId(10L);
        summary.setStatus("SUCCESS");
        when(traceService.startTrace(any(), any(), any(), eq("MERCHANT_REVIEW_SUMMARY"))).thenReturn(context);
        when(service.generateSummary(10L, false, context)).thenReturn(summary);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MerchantReviewSummaryVO body = controller.generate(10L, false, response).data();

        assertEquals("trc-summary", body.getTraceId());
        assertEquals("trc-summary", response.getHeader("X-Trace-Id"));
        verify(traceService, times(1)).startTrace(any(), any(), any(), eq("MERCHANT_REVIEW_SUMMARY"));
        verify(service).generateSummary(10L, false, context);
    }

    @Test
    void highlightGenerationUsesOneRootForAllReturnedHighlights() {
        MerchantHighlightService service = mock(MerchantHighlightService.class);
        AiRequestTraceService traceService = mock(AiRequestTraceService.class);
        MerchantHighlightController controller = new MerchantHighlightController(service, traceService);
        AiTraceContext context = new AiTraceContext("trc-highlights", "req-highlights", null, null,
                "MERCHANT_HIGHLIGHT_GENERATION");
        MerchantHighlightVO first = highlight(1L);
        MerchantHighlightVO second = highlight(2L);
        when(traceService.startTrace(any(), any(), any(), eq("MERCHANT_HIGHLIGHT_GENERATION"))).thenReturn(context);
        when(service.generateHighlights(10L, false, context)).thenReturn(List.of(first, second));

        MockHttpServletResponse response = new MockHttpServletResponse();
        List<MerchantHighlightVO> body = controller.generate(10L, false, response).data();

        assertEquals(List.of("trc-highlights", "trc-highlights"), body.stream()
                .map(MerchantHighlightVO::getTraceId).toList());
        assertEquals("trc-highlights", response.getHeader("X-Trace-Id"));
        verify(traceService, times(1)).startTrace(any(), any(), any(), eq("MERCHANT_HIGHLIGHT_GENERATION"));
        verify(service).generateHighlights(10L, false, context);
    }

    private MerchantHighlightVO highlight(Long id) {
        MerchantHighlightVO result = new MerchantHighlightVO();
        result.setHighlightId(id);
        result.setMerchantId(10L);
        result.setStatus("ACTIVE");
        return result;
    }
}
