package com.foodadvisor.service;

import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.entity.MerchantHighlight;
import com.foodadvisor.entity.MerchantHighlightEvidence;
import com.foodadvisor.entity.Review;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MerchantHighlightMatchingServiceTest {
    private final MerchantHighlightMatchingService service =
            new MerchantHighlightMatchingService();

    @Test
    void matchesVisibleSameMerchantEnvironmentEvidenceOnly() {
        ConstraintState state = new ConstraintState();
        state.setEnvironmentRequirements(List.of("安静"));
        MerchantHighlight highlight = highlight(10L, 1L,
                "ENVIRONMENT", "环境安静", "多条评价提到环境安静");
        MerchantHighlightEvidence link = link(10L, 100L);
        Review review = review(100L, 1L, "PUBLISHED", "APPROVED");

        var result = service.match(state, List.of(highlight),
                List.of(link), Map.of(100L, review));

        assertAll(
                () -> assertEquals(1, result.get(1L).size()),
                () -> assertEquals("REVIEW",
                        result.get(1L).get(0).getSourceType()),
                () -> assertEquals(1L,
                        result.get(1L).get(0).getMerchantId())
        );
    }

    @Test
    void rejectsInvisibleCrossMerchantAndLimitsToThree() {
        ConstraintState state = new ConstraintState();
        state.setScenes(List.of("朋友聚会"));
        List<MerchantHighlight> highlights = new ArrayList<>();
        List<MerchantHighlightEvidence> links = new ArrayList<>();
        java.util.Map<Long, Review> reviews = new java.util.HashMap<>();
        for (long id = 1; id <= 5; id++) {
            highlights.add(highlight(id, 1L, "ENVIRONMENT",
                    "适合朋友聚会" + id, "聚会环境"));
            links.add(link(id, 100L + id));
            reviews.put(100L + id, review(100L + id,
                    id == 5 ? 2L : 1L,
                    id == 4 ? "DELETED" : "PUBLISHED", "APPROVED"));
        }

        var result = service.match(state, highlights, links, reviews);

        assertEquals(3, result.get(1L).size());
    }

    @Test
    void irrelevantConstraintsDoNotRequestHighlightMatching() {
        assertFalse(service.isRelevant(new ConstraintState()));
    }

    @Test
    void twoVisibleReviewsKeepMultipleReviewClaim() {
        ConstraintState state = new ConstraintState();
        state.setEnvironmentRequirements(List.of("安静"));
        MerchantHighlight highlight = highlight(10L, 1L,
                "ENVIRONMENT", "环境安静", "多条评价提到环境安静");
        var result = service.match(
                state,
                List.of(highlight),
                List.of(link(10L, 100L), link(10L, 101L)),
                Map.of(
                        100L, review(100L, 1L, "PUBLISHED", "APPROVED"),
                        101L, review(101L, 1L, "PUBLISHED", "APPROVED")));

        assertTrue(result.get(1L).get(0).getSummary().contains("多条评价"));
    }

    @Test
    void oneVisibleReviewDowngradesMultipleReviewClaim() {
        ConstraintState state = new ConstraintState();
        state.setEnvironmentRequirements(List.of("安静"));
        MerchantHighlight highlight = highlight(10L, 1L,
                "ENVIRONMENT", "环境安静", "多条评价提到环境安静");
        var result = service.match(
                state,
                List.of(highlight),
                List.of(link(10L, 100L), link(10L, 101L)),
                Map.of(
                        100L, review(100L, 1L, "PUBLISHED", "APPROVED"),
                        101L, review(101L, 1L, "DELETED", "APPROVED")));

        assertFalse(result.get(1L).get(0).getSummary().contains("多条"));
        assertTrue(result.get(1L).get(0).getSummary().contains("有评价"));
    }

    @Test
    void zeroVisibleReviewsProduceNoReviewBasis() {
        ConstraintState state = new ConstraintState();
        state.setEnvironmentRequirements(List.of("安静"));
        MerchantHighlight highlight = highlight(10L, 1L,
                "ENVIRONMENT", "环境安静", "多条评价提到环境安静");

        assertTrue(service.match(
                state, List.of(highlight), List.of(link(10L, 100L)),
                Map.of(100L, review(100L, 1L, "HIDDEN", "APPROVED")))
                .isEmpty());
    }

    private MerchantHighlight highlight(Long id, Long merchantId,
                                        String type, String title, String description) {
        MerchantHighlight value = new MerchantHighlight();
        value.setId(id);
        value.setMerchantId(merchantId);
        value.setHighlightType(type);
        value.setTitle(title);
        value.setDescription(description);
        return value;
    }
    private MerchantHighlightEvidence link(Long highlightId, Long reviewId) {
        MerchantHighlightEvidence value = new MerchantHighlightEvidence();
        value.setHighlightId(highlightId);
        value.setReviewId(reviewId);
        return value;
    }
    private Review review(Long id, Long merchantId, String status, String moderation) {
        Review value = new Review();
        value.setId(id);
        value.setMerchantId(merchantId);
        value.setStatus(status);
        value.setModerationStatus(moderation);
        return value;
    }
}
