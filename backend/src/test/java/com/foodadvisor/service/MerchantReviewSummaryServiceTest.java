package com.foodadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.summary.SummaryEvidenceVO;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.MerchantReviewSummary;
import com.foodadvisor.entity.MerchantSummaryEvidence;
import com.foodadvisor.entity.Review;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.MerchantReviewSummaryMapper;
import com.foodadvisor.mapper.MerchantSummaryEvidenceMapper;
import com.foodadvisor.mapper.ReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantReviewSummaryServiceTest {

    @Mock MerchantReviewSummaryMapper summaryMapper;
    @Mock MerchantSummaryEvidenceMapper evidenceMapper;
    @Mock ReviewMapper reviewMapper;
    @Mock MerchantMapper merchantMapper;
    @Mock AIClientService aiClientService;
    @Mock JdbcTemplate jdbcTemplate;

    private MerchantReviewSummaryService service;

    @BeforeEach
    void setUp() {
        service = new MerchantReviewSummaryService(
                summaryMapper, evidenceMapper, reviewMapper, merchantMapper,
                aiClientService, jdbcTemplate, new ObjectMapper());
    }

    @Test
    void publicApprovedSameMerchantReviewReturnsTraceableSource() {
        arrangeSummaryAndEvidence(10L, 20L);
        Review review = review(20L, 10L, "PUBLISHED", "APPROVED", null);
        review.setContent("真实公开评价");
        review.setRating(new BigDecimal("5"));
        review.setPublishedAt(OffsetDateTime.parse("2026-07-01T10:00:00+08:00"));
        when(reviewMapper.selectBatchIds(any())).thenReturn(List.of(review));

        SummaryEvidenceVO result = service
                .getEvidences(10L, 1L, null).get(0);

        assertAll(
                () -> assertTrue(result.getAvailable()),
                () -> assertEquals("REVIEW", result.getSourceType()),
                () -> assertEquals(20L, result.getSourceId()),
                () -> assertEquals(10L, result.getMerchantId()),
                () -> assertEquals("测试商家", result.getMerchantName()),
                () -> assertEquals("真实公开评价", result.getReviewContent()),
                () -> assertEquals("真实片段", result.getEvidenceExcerpt())
        );
    }

    @Test
    void deletedRejectedPendingDeletedAtMissingAndCrossMerchantNeverLeak() {
        for (Review review : List.of(
                review(20L, 10L, "DELETED", "APPROVED", null),
                review(20L, 10L, "PUBLISHED", "REJECTED", null),
                review(20L, 10L, "PUBLISHED", "PENDING", null),
                review(20L, 10L, "PUBLISHED", "APPROVED", OffsetDateTime.now()),
                review(20L, 99L, "PUBLISHED", "APPROVED", null))) {
            arrangeSummaryAndEvidence(10L, 20L);
            when(reviewMapper.selectBatchIds(any())).thenReturn(List.of(review));

            assertUnavailable(service.getEvidences(10L, 1L, null).get(0));
            clearInvocations(summaryMapper, evidenceMapper, reviewMapper,
                    merchantMapper);
        }

        arrangeSummaryAndEvidence(10L, 20L);
        when(reviewMapper.selectBatchIds(any())).thenReturn(List.of());
        assertUnavailable(service.getEvidences(10L, 1L, null).get(0));
    }

    @Test
    void mismatchedStoredSourceMerchantNeverLeaks() {
        arrangeSummaryAndEvidence(99L, 20L);
        Review review = review(20L, 10L, "PUBLISHED", "APPROVED", null);
        when(reviewMapper.selectBatchIds(any())).thenReturn(List.of(review));

        assertUnavailable(service.getEvidences(10L, 1L, null).get(0));
    }

    @Test
    void summaryFromAnotherPathMerchantIsRejected() {
        MerchantReviewSummary summary = summary(10L);
        when(summaryMapper.selectById(1L)).thenReturn(summary);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.getEvidences(99L, 1L, null));

        assertEquals("SUMMARY_NOT_FOUND", exception.getCode());
        verifyNoInteractions(evidenceMapper, reviewMapper);
    }

    @Test
    void noEvidenceReturnsEmptyList() {
        when(summaryMapper.selectById(1L)).thenReturn(summary(10L));
        when(evidenceMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.getEvidences(10L, 1L, null).isEmpty());
        verifyNoInteractions(reviewMapper, merchantMapper);
    }

    private void arrangeSummaryAndEvidence(
            Long sourceMerchantId,
            Long reviewId
    ) {
        when(summaryMapper.selectById(1L)).thenReturn(summary(10L));
        MerchantSummaryEvidence evidence = new MerchantSummaryEvidence();
        evidence.setId(30L);
        evidence.setSummaryId(1L);
        evidence.setReviewId(reviewId);
        evidence.setSourceType("REVIEW");
        evidence.setSourceMerchantId(sourceMerchantId);
        evidence.setEvidenceType("ADVANTAGE");
        evidence.setEvidenceExcerpt("真实片段");
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence));
        Merchant merchant = new Merchant();
        merchant.setId(10L);
        merchant.setName("测试商家");
        when(merchantMapper.selectById(10L)).thenReturn(merchant);
    }

    private MerchantReviewSummary summary(Long merchantId) {
        MerchantReviewSummary summary = new MerchantReviewSummary();
        summary.setId(1L);
        summary.setMerchantId(merchantId);
        summary.setVersion(2);
        summary.setStatus("SUCCESS");
        return summary;
    }

    private Review review(
            Long id,
            Long merchantId,
            String status,
            String moderationStatus,
            OffsetDateTime deletedAt
    ) {
        Review review = new Review();
        review.setId(id);
        review.setMerchantId(merchantId);
        review.setStatus(status);
        review.setModerationStatus(moderationStatus);
        review.setDeletedAt(deletedAt);
        review.setContent("不得泄露的正文");
        return review;
    }

    private void assertUnavailable(SummaryEvidenceVO result) {
        assertAll(
                () -> assertFalse(result.getAvailable()),
                () -> assertFalse(result.getReviewAvailable()),
                () -> assertEquals("SOURCE_UNAVAILABLE",
                        result.getUnavailableReason()),
                () -> assertNull(result.getSourceId()),
                () -> assertNull(result.getReviewId()),
                () -> assertNull(result.getReviewContent()),
                () -> assertNull(result.getEvidenceExcerpt()),
                () -> assertNull(result.getReviewTime()),
                () -> assertNull(result.getRating())
        );
    }
}
