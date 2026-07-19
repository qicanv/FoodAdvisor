package com.foodadvisor.controller;

import com.foodadvisor.dto.summary.SummaryEvidenceVO;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.service.MerchantReviewSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MerchantReviewSummaryControllerTest {

    private MerchantReviewSummaryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MerchantReviewSummaryService.class);
        mockMvc = standaloneSetup(new MerchantReviewSummaryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsAvailableEvidence() throws Exception {
        SummaryEvidenceVO evidence = new SummaryEvidenceVO();
        evidence.setEvidenceId(3L);
        evidence.setSourceType("REVIEW");
        evidence.setAvailable(true);
        evidence.setMerchantId(10L);
        when(service.getEvidences(10L, 2L, null))
                .thenReturn(List.of(evidence));

        mockMvc.perform(get("/api/merchants/10/review-summary/evidences")
                        .param("summaryId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceType").value("REVIEW"))
                .andExpect(jsonPath("$.data[0].available").value(true));
    }

    @Test
    void noEvidenceReturnsHttp200EmptyList() throws Exception {
        when(service.getEvidences(10L, null, null))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/merchants/10/review-summary/evidences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void unavailableEvidenceHasNoSensitiveFields() throws Exception {
        SummaryEvidenceVO evidence = new SummaryEvidenceVO();
        evidence.setSourceType("REVIEW");
        evidence.setAvailable(false);
        evidence.setUnavailableReason("SOURCE_UNAVAILABLE");
        when(service.getEvidences(10L, 2L, null))
                .thenReturn(List.of(evidence));

        mockMvc.perform(get("/api/merchants/10/review-summary/evidences")
                        .param("summaryId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].available").value(false))
                .andExpect(jsonPath("$.data[0].reviewContent").isEmpty())
                .andExpect(jsonPath("$.data[0].evidenceExcerpt").isEmpty());
    }

    @Test
    void malformedSummaryIdUsesUnifiedErrorResponse() throws Exception {
        mockMvc.perform(get("/api/merchants/10/review-summary/evidences")
                        .param("summaryId", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").exists());
    }
}
