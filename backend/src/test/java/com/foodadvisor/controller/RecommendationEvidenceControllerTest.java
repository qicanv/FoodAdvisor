package com.foodadvisor.controller;

import com.foodadvisor.dto.recommendation.RecommendationEvidenceDetailVO;
import com.foodadvisor.service.RecommendationEvidenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecommendationEvidenceControllerTest {
    @Mock
    private RecommendationEvidenceService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RecommendationEvidenceController(service))
                .defaultRequest(get("/").requestAttr("userId", 7L))
                .build();
    }

    @Test
    void passesAuthenticatedUserRecommendationAndMerchantToService()
            throws Exception {
        RecommendationEvidenceDetailVO evidence =
                new RecommendationEvidenceDetailVO();
        evidence.setMerchantId(31L);
        evidence.setSourceType("REVIEW");
        evidence.setAvailable(true);
        when(service.list(7L, 11L, 31L)).thenReturn(List.of(evidence));

        mockMvc.perform(get("/api/diner/recommendations/11/evidences")
                        .param("merchantId", "31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].merchantId").value(31));

        verify(service).list(7L, 11L, 31L);
    }

    @Test
    void merchantIdIsRequired() throws Exception {
        mockMvc.perform(get("/api/diner/recommendations/11/evidences"))
                .andExpect(status().isBadRequest());
    }
}
