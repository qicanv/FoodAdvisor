package com.foodadvisor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.AdjustmentSuggestionVO;
import com.foodadvisor.dto.recommendation.RecommendationItemVO;
import com.foodadvisor.dto.recommendation.RecommendationRankRequest;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import com.foodadvisor.service.RecommendationRankingService;
import com.foodadvisor.service.DiningDialogueMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private RecommendationRankingService recommendationRankingService;

    @Mock
    private DiningDialogueMessageService diningDialogueMessageService;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new RecommendationController(
                                recommendationRankingService,
                                diningDialogueMessageService
                        )
                )
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .defaultRequest(
                        get("/").requestAttr("userId", 1L)
                )
                .build();
    }

    @Test
    void shouldReturnRankSuccess() throws Exception {
        when(recommendationRankingService.rank(
                eq(1L),
                any(RecommendationRankRequest.class)
        )).thenReturn(successResponse());

        mockMvc.perform(post(
                        "/api/diner/sessions/1/recommendations/rank"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rankRequest()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.results[0].merchantId")
                        .value(701));
    }

    @Test
    void shouldReturnRankNoMatch() throws Exception {
        when(recommendationRankingService.rank(
                eq(1L),
                any(RecommendationRankRequest.class)
        )).thenReturn(noMatchResponse());

        mockMvc.perform(post(
                        "/api/diner/sessions/1/recommendations/rank"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rankRequest()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.matched").value(false))
                .andExpect(jsonPath("$.data.status").value("NO_MATCH"))
                .andExpect(jsonPath("$.data.message")
                        .value("当前没有完全匹配的结果"))
                .andExpect(jsonPath("$.data.results").isArray())
                .andExpect(jsonPath("$.data.results").isEmpty())
                .andExpect(jsonPath("$.data.adjustmentSuggestions[0]")
                        .exists());
    }

    @Test
    void shouldMapDataServiceExceptionToHttp503() throws Exception {
        when(recommendationRankingService.rank(
                eq(1L),
                any(RecommendationRankRequest.class)
        )).thenThrow(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RECOMMENDATION_DATA_SERVICE_ERROR",
                "推荐数据服务暂时不可用，请稍后重试"
        ));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/recommendations/rank"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rankRequest()
                        )))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("RECOMMENDATION_DATA_SERVICE_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("推荐数据服务暂时不可用，请稍后重试"));
    }

    @Test
    void shouldMapMissingLocationToHttp400() throws Exception {
        when(recommendationRankingService.rank(
                eq(1L),
                any(RecommendationRankRequest.class)
        )).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST,
                "USER_LOCATION_REQUIRED",
                "当前位置是距离筛选的必填条件"
        ));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/recommendations/rank"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                rankRequest()
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("USER_LOCATION_REQUIRED"));
    }

    @Test
    void shouldMapInvalidAdjustmentToHttp400() throws Exception {
        when(diningDialogueMessageService.adjustRecommendation(
                eq(1L),
                any()
        )).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_RECOMMENDATION_ADJUSTMENT",
                "distanceKm must be greater than 0"
        ));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/recommendations/adjust"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "userId",
                                        1,
                                        "sourceMessageId",
                                        71,
                                        "field",
                                        "distanceKm",
                                        "value",
                                        0
                                )
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_RECOMMENDATION_ADJUSTMENT"));
    }

    @Test
    void shouldReturnFullRecommendationAfterAdjust() throws Exception {
        when(diningDialogueMessageService.adjustRecommendation(
                eq(1L),
                any()
        )).thenReturn(successResponse());

        mockMvc.perform(post(
                        "/api/diner/sessions/1/recommendations/adjust"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "userId",
                                        1,
                                        "sourceMessageId",
                                        71,
                                        "field",
                                        "perCapitaBudget",
                                        "value",
                                        120
                                )
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.results[0].merchantId")
                        .value(701));
    }

    @Test
    void shouldUseJwtUserIdForRankAndIgnoreBodyUserId()
            throws Exception {
        when(recommendationRankingService.rank(
                eq(1L),
                any(RecommendationRankRequest.class)
        )).thenReturn(successResponse());

        RecommendationRankRequest request = rankRequest();
        request.setUserId(999L);

        mockMvc.perform(post(
                        "/api/diner/sessions/1/recommendations/rank"
                )
                        .requestAttr("userId", 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(recommendationRankingService).rank(
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(
                        value -> Long.valueOf(7L)
                                .equals(value.getUserId())
                )
        );
    }

    @Test
    void shouldUseJwtUserIdForAdjustAndIgnoreBodyUserId()
            throws Exception {
        when(diningDialogueMessageService.adjustRecommendation(
                eq(1L),
                any()
        )).thenReturn(successResponse());

        mockMvc.perform(post(
                        "/api/diner/sessions/1/recommendations/adjust"
                )
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of(
                                        "userId", 999,
                                        "sourceMessageId", 71,
                                        "field", "perCapitaBudget",
                                        "value", 120
                                )
                        )))
                .andExpect(status().isOk());

        verify(diningDialogueMessageService).adjustRecommendation(
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(
                        value -> Long.valueOf(7L)
                                .equals(value.getUserId())
                )
        );
    }

    private RecommendationRankRequest rankRequest() {
        RecommendationRankRequest request =
                new RecommendationRankRequest();
        request.setUserId(1L);
        request.setUserLatitude(new BigDecimal("30.5728"));
        request.setUserLongitude(new BigDecimal("104.0668"));
        return request;
    }

    private RecommendationRankResponse successResponse() {
        RecommendationRankResponse response =
                baseResponse();
        response.setMatched(true);
        response.setStatus("SUCCESS");
        response.setMessage("推荐完成");
        response.setResultCount(1);
        response.setResults(
                List.of(recommendationItem())
        );
        return response;
    }

    private RecommendationRankResponse noMatchResponse() {
        RecommendationRankResponse response =
                baseResponse();
        response.setMatched(false);
        response.setStatus("NO_MATCH");
        response.setMessage("当前没有完全匹配的结果");
        response.setResultCount(0);
        response.setResults(List.of());
        response.setAdjustmentSuggestions(
                List.of(new AdjustmentSuggestionVO(
                        "relax-cuisine-general",
                        "RELAX_CUISINE",
                        "cuisines",
                        List.of("sichuan"),
                        List.of(),
                        "暂时取消菜系限制并重新搜索",
                        "当前没有完全匹配结果，可以先放宽菜系要求"
                ))
        );
        return response;
    }

    private RecommendationRankResponse baseResponse() {
        RecommendationRankResponse response =
                new RecommendationRankResponse();
        response.setRecommendationId(7001L);
        response.setSessionId(1L);
        response.setRequestId("request-1");
        response.setAlgorithmVersion("RULE_V1");
        response.setConstraints(new ConstraintState());
        response.setCurrentConstraints(new ConstraintState());
        return response;
    }

    private RecommendationItemVO recommendationItem() {
        RecommendationItemVO item =
                new RecommendationItemVO();
        item.setMerchantId(701L);
        item.setMerchantName("Test Merchant");
        item.setFinalScore(new BigDecimal("95.00"));
        return item;
    }
}
