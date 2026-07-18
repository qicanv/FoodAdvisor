package com.foodadvisor.controller;

import com.foodadvisor.dto.review.ReviewSubmitRequest;
import com.foodadvisor.dto.review.ReviewSubmitResponse;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.mapper.ReviewIssueCategoryMapper;
import com.foodadvisor.mapper.ReviewTagMapper;
import com.foodadvisor.service.AIClientService;
import com.foodadvisor.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ReviewIssueCategoryMapper issueCategoryMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ReviewController controller = new ReviewController(
                reviewService,
                aiClientService,
                reviewTagMapper,
                jdbcTemplate,
                issueCategoryMapper
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
}
