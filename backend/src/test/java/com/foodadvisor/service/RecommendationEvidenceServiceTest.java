package com.foodadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.entity.*;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationEvidenceServiceTest {
    @Mock RecommendationMapper recommendationMapper;
    @Mock RecommendationItemMapper itemMapper;
    @Mock RecommendationEvidenceMapper evidenceMapper;
    @Mock ReviewMapper reviewMapper;
    @Mock MerchantMapper merchantMapper;
    RecommendationEvidenceService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationEvidenceService(
                recommendationMapper, itemMapper, evidenceMapper,
                reviewMapper, merchantMapper, new ObjectMapper());
    }

    @Test
    void ownerSeesOnlyRequestedMerchantCurrentVisibleReview() {
        arrangeRecommendation(1L, 10L, 20L, 200L);
        OffsetDateTime published = OffsetDateTime.now().minusDays(1);
        Review review = review(300L, 20L, "PUBLISHED", "APPROVED", null);
        review.setContent("当前公开评论正文");
        review.setPublishedAt(published);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(
                evidence(200L, 20L, "REVIEW", 300L,
                        "{\"title\":\"环境安静\",\"summary\":\"旧评论正文\"}")));
        when(reviewMapper.selectByIds(any())).thenReturn(List.of(review));

        var result = service.list(1L, 10L, 20L);

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertTrue(result.get(0).getAvailable()),
                () -> assertEquals("当前公开评论正文", result.get(0).getExcerpt()),
                () -> assertEquals(published, result.get(0).getReviewTime()),
                () -> assertEquals("商家20", result.get(0).getMerchantName()),
                () -> assertEquals("环境安静", result.get(0).getHighlightTitle()),
                () -> assertEquals("environmentRequirements",
                        result.get(0).getConditionKey())
        );
        verify(itemMapper).selectList(any());
    }

    @Test
    void otherUserRecommendationIsTreatedAsMissing() {
        Recommendation recommendation = recommendation(2L, 10L);
        when(recommendationMapper.selectById(10L)).thenReturn(recommendation);

        assertThrows(ApiException.class,
                () -> service.list(1L, 10L, 20L));
        verifyNoInteractions(itemMapper, evidenceMapper, reviewMapper);
    }

    @Test
    void merchantWithoutRecommendationItemReturnsEmptyWithoutEvidenceQuery() {
        when(recommendationMapper.selectById(10L))
                .thenReturn(recommendation(1L, 10L));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.list(1L, 10L, 99L).isEmpty());
        verifyNoInteractions(evidenceMapper, reviewMapper, merchantMapper);
    }

    @Test
    void deletedRejectedHiddenPendingMissingAndCrossMerchantReviewsNeverLeakSnapshot() {
        String leaked = "快照里的旧评论正文不应泄露";
        for (Review review : List.of(
                review(301L, 20L, "PUBLISHED", "APPROVED", OffsetDateTime.now()),
                review(302L, 20L, "PUBLISHED", "REJECTED", null),
                review(303L, 20L, "HIDDEN", "APPROVED", null),
                review(304L, 20L, "PENDING", "PENDING", null))) {
            arrangeRecommendation(1L, 10L, 20L, 200L);
            when(evidenceMapper.selectList(any())).thenReturn(List.of(
                    evidence(200L, 20L, "REVIEW", review.getId(),
                            "{\"title\":\"亮点\",\"content\":\"" + leaked + "\"}")));
            when(reviewMapper.selectByIds(any())).thenReturn(List.of(review));

            var value = service.list(1L, 10L, 20L).get(0);
            assertFalse(value.getAvailable());
            assertNull(value.getExcerpt());
            assertNotNull(value.getUnavailableReason());
            clearInvocations(recommendationMapper, itemMapper, evidenceMapper,
                    reviewMapper, merchantMapper);
        }

        arrangeRecommendation(1L, 10L, 20L, 200L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(
                evidence(200L, 20L, "REVIEW", 999L,
                        "{\"content\":\"" + leaked + "\"}")));
        when(reviewMapper.selectByIds(any())).thenReturn(List.of());
        assertNull(service.list(1L, 10L, 20L).get(0).getExcerpt());
    }

    @Test
    void crossMerchantReviewIsNotReturned() {
        arrangeRecommendation(1L, 10L, 20L, 200L);
        Review review = review(
                305L, 99L, "PUBLISHED", "APPROVED", null);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(
                evidence(200L, 20L, "REVIEW", 305L,
                        "{\"content\":\"其他商家评论\"}")));
        when(reviewMapper.selectByIds(any())).thenReturn(List.of(review));

        assertTrue(service.list(1L, 10L, 20L).isEmpty());
    }

    @Test
    void merchantDishBrokenSnapshotAndGoodEvidenceAreReturnedSafely() {
        arrangeRecommendation(1L, 10L, 20L, 200L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(
                evidence(200L, 20L, "MERCHANT", null, "{broken"),
                evidence(200L, 20L, "DISH", null,
                        "{\"dishName\":\"水煮鱼\",\"dishPrice\":68}"),
                evidence(200L, 20L, "DISH", null, "{broken")));

        var result = service.list(1L, 10L, 20L);

        assertAll(
                () -> assertEquals(3, result.size()),
                () -> assertEquals("MERCHANT", result.get(0).getSourceType()),
                () -> assertEquals("DISH", result.get(1).getSourceType()),
                () -> assertTrue(result.get(1).getExcerpt().contains("水煮鱼"))
        );
    }

    @Test
    void crossMerchantEvidenceIsSkippedAndNoEvidenceReturnsEmpty() {
        arrangeRecommendation(1L, 10L, 20L, 200L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(
                evidence(200L, 99L, "MERCHANT", null, "{}")));
        assertTrue(service.list(1L, 10L, 20L).isEmpty());

        when(evidenceMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.list(1L, 10L, 20L).isEmpty());
    }

    @Test
    void reviewTextIsLimitedAndNoPrivateUserFieldsAreExposed() {
        arrangeRecommendation(1L, 10L, 20L, 200L);
        Review review = review(300L, 20L, "PUBLISHED", "APPROVED", null);
        review.setUserId(9988L);
        review.setSourceUserKey("phone@example.com");
        review.setContent("x".repeat(1000));
        when(reviewMapper.selectByIds(any())).thenReturn(List.of(review));
        when(evidenceMapper.selectList(any())).thenReturn(List.of(
                evidence(200L, 20L, "REVIEW", 300L, "{}")));

        var value = service.list(1L, 10L, 20L).get(0);
        assertEquals(800, value.getExcerpt().length());
        assertFalse(value.toString().contains("9988"));
        assertFalse(value.toString().contains("phone@example.com"));
    }

    private void arrangeRecommendation(Long userId, Long recommendationId,
                                       Long merchantId, Long itemId) {
        when(recommendationMapper.selectById(recommendationId))
                .thenReturn(recommendation(userId, recommendationId));
        RecommendationItem item = new RecommendationItem();
        item.setId(itemId);
        item.setRecommendationId(recommendationId);
        item.setMerchantId(merchantId);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        Merchant merchant = new Merchant();
        merchant.setId(merchantId);
        merchant.setName("商家" + merchantId);
        when(merchantMapper.selectById(merchantId)).thenReturn(merchant);
    }
    private Recommendation recommendation(Long userId, Long id) {
        Recommendation value = new Recommendation();
        value.setId(id); value.setUserId(userId); return value;
    }
    private Review review(Long id, Long merchantId, String status,
                          String moderation, OffsetDateTime deletedAt) {
        Review value = new Review();
        value.setId(id); value.setMerchantId(merchantId);
        value.setStatus(status); value.setModerationStatus(moderation);
        value.setDeletedAt(deletedAt); return value;
    }
    private RecommendationEvidence evidence(
            Long itemId, Long merchantId, String type,
            Long reviewId, String snapshot) {
        RecommendationEvidence value = new RecommendationEvidence();
        value.setId((long) (Math.random() * 100000));
        value.setRecommendationItemId(itemId);
        value.setSourceMerchantId(merchantId);
        value.setSourceType(type);
        value.setReviewId(reviewId);
        value.setConditionKey("environmentRequirements");
        value.setSourceTextSnapshot(snapshot);
        value.setEvidenceExcerpt(type + "摘要");
        return value;
    }
}
