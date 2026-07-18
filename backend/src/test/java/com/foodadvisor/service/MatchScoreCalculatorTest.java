package com.foodadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.RecommendationItemVO;
import com.foodadvisor.dto.recommendation.RecommendationWeights;
import com.foodadvisor.entity.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchScoreCalculatorTest {

    private MatchScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MatchScoreCalculator(
                new ObjectMapper()
        );
    }

    @Test
    void shouldCalculateExpectedScoreForMatchedMerchant() {
        Optional<RecommendationItemVO> optionalResult =
                calculator.calculate(
                        createBaseMerchant(),
                        createBaseConstraints(),
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(optionalResult.isPresent());

        RecommendationItemVO result =
                optionalResult.orElseThrow();

        assertAll(
                () -> assertEquals(
                        0,
                        new BigDecimal("97.31")
                                .compareTo(
                                        result.getFinalScore()
                                )
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("0.03")
                                .compareTo(
                                        result.getDistanceKm()
                                )
                ),
                () -> assertEquals(
                        6,
                        result.getScoreItems().size()
                ),
                () -> assertTrue(
                        result.getScoreItems()
                                .containsKey("cuisine")
                ),
                () -> assertTrue(
                        result.getScoreItems()
                                .containsKey("rating")
                ),
                () -> assertTrue(
                        result.getScoreItems()
                                .containsKey("price")
                ),
                () -> assertTrue(
                        result.getScoreItems()
                                .containsKey("distance")
                ),
                () -> assertTrue(
                        result.getScoreItems()
                                .containsKey("environment")
                ),
                () -> assertTrue(
                        result.getScoreItems()
                                .containsKey("reputation")
                ),
                () -> assertTrue(
                        result.getMatchedConditions()
                                .size() >= 2
                ),
                () -> assertTrue(
                        result.getRiskNotes().isEmpty()
                ),
                () -> assertEquals(
                        "OPERATING",
                        result.getOperationStatus()
                )
        );
    }

    @Test
    void shouldFilterMerchantWhenPriceExceedsBudget() {
        Merchant merchant = createBaseMerchant();
        merchant.setAveragePrice(
                new BigDecimal("80.01")
        );

        Optional<RecommendationItemVO> result =
                calculator.calculate(
                        merchant,
                        createBaseConstraints(),
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFilterMerchantWhenTypeIsExplicitlyExcluded() {
        Merchant merchant = createBaseMerchant();
        merchant.setName("hotpot restaurant");
        merchant.setCategory("hotpot");

        ConstraintState constraints =
                createBaseConstraints();
        constraints.setExcludedMerchantTypes(
                List.of("hotpot")
        );

        Optional<RecommendationItemVO> result =
                calculator.calculate(
                        merchant,
                        constraints,
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFilterMerchantWhenOperationIsSuspended() {
        Merchant merchant = createBaseMerchant();
        merchant.setOperationStatus("SUSPENDED");

        Optional<RecommendationItemVO> result =
                calculator.calculate(
                        merchant,
                        createBaseConstraints(),
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFilterMerchantWhenDistanceExceedsLimit() {
        Merchant merchant = createBaseMerchant();
        merchant.setLongitude(
                new BigDecimal("104.150000")
        );
        merchant.setLatitude(
                new BigDecimal("30.600000")
        );

        Optional<RecommendationItemVO> result =
                calculator.calculate(
                        merchant,
                        createBaseConstraints(),
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFilterMerchantWhenCuisineDoesNotMatch() {
        Merchant merchant = createBaseMerchant();
        merchant.setCuisine("cantonese");
        merchant.setCategory("cantonese");

        Optional<RecommendationItemVO> result =
                calculator.calculate(
                        merchant,
                        createBaseConstraints(),
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldMatchAnyCuisineInCuisineList() {
        ConstraintState constraints =
                createBaseConstraints();
        constraints.setCuisines(
                List.of("cantonese", "sichuan")
        );

        Optional<RecommendationItemVO> result =
                calculator.calculate(
                        createBaseMerchant(),
                        constraints,
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(result.isPresent());
    }

    @Test
    void shouldRequireAllEnvironmentRequirements() {
        ConstraintState constraints =
                createBaseConstraints();
        constraints.setEnvironmentRequirements(
                List.of("quiet", "photo-friendly")
        );

        Optional<RecommendationItemVO> result =
                calculator.calculate(
                        createBaseMerchant(),
                        constraints,
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFilterMerchantWhenMinRatingDoesNotMatch() {
        ConstraintState constraints =
                createBaseConstraints();
        constraints.setMinRating(
                new BigDecimal("4.9")
        );

        Optional<RecommendationItemVO> result =
                calculator.calculate(
                        createBaseMerchant(),
                        constraints,
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldMatchAnySceneInSceneList() {
        ConstraintState constraints =
                createBaseConstraints();
        constraints.setScenes(
                List.of("date", "friends")
        );

        Optional<RecommendationItemVO> result =
                calculator.calculate(
                        createBaseMerchant(),
                        constraints,
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(result.isPresent());
    }

    @Test
    void shouldBuildReasonWithAtLeastTwoRealFacts() {
        RecommendationItemVO result =
                calculator.calculate(
                        createBaseMerchant(),
                        createBaseConstraints(),
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                ).orElseThrow();

        assertAll(
                () -> assertTrue(
                        result.getMatchedConditions()
                                .size() >= 2
                ),
                () -> assertTrue(
                        result.getReason() != null
                                && !result.getReason().isBlank()
                )
        );
    }

    @Test
    void shouldNotInventPriceFactWhenMerchantPriceIsMissing() {
        Merchant merchant = createBaseMerchant();
        merchant.setAveragePrice(null);

        ConstraintState constraints =
                createBaseConstraints();
        constraints.setPerCapitaBudget(null);

        RecommendationItemVO result =
                calculator.calculate(
                        merchant,
                        constraints,
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                ).orElseThrow();

        assertAll(
                () -> assertEquals(
                        null,
                        result.getAveragePrice()
                ),
                () -> assertTrue(
                        result.getReason() != null
                                && !result.getReason()
                                .contains("68")
                )
        );
    }

    @Test
    void shouldNotInventRatingFactWhenMerchantRatingIsMissing() {
        Merchant merchant = createBaseMerchant();
        merchant.setRating(null);

        ConstraintState constraints =
                createBaseConstraints();
        constraints.setMinRating(null);

        RecommendationItemVO result =
                calculator.calculate(
                        merchant,
                        constraints,
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                ).orElseThrow();

        assertAll(
                () -> assertEquals(
                        null,
                        result.getMerchantRating()
                ),
                () -> assertTrue(
                        result.getReason() != null
                                && !result.getReason()
                                .contains("4.80")
                )
        );
    }

    @Test
    void shouldUseRealStatusFactsWhenFewUserFactsAreAvailable() {
        ConstraintState constraints =
                new ConstraintState();

        RecommendationItemVO result =
                calculator.calculate(
                        createBaseMerchant(),
                        constraints,
                        createDefaultWeights(),
                        null,
                        null
                ).orElseThrow();

        assertAll(
                () -> assertTrue(
                        result.getMatchedConditions()
                                .size() >= 2
                ),
                () -> assertTrue(
                        result.getReason() != null
                                && !result.getReason().isBlank()
                )
        );
    }

    private Merchant createBaseMerchant() {
        Merchant merchant = new Merchant();

        merchant.setId(2L);
        merchant.setMerchantCode("RANK_TEST_001");
        merchant.setName("sichuan bistro");
        merchant.setCategory("sichuan");
        merchant.setCuisine("sichuan");
        merchant.setRating(
                new BigDecimal("4.80")
        );
        merchant.setAveragePrice(
                new BigDecimal("68.00")
        );
        merchant.setReviewCount(160);
        merchant.setAddress("test address");
        merchant.setLongitude(
                new BigDecimal("104.067000")
        );
        merchant.setLatitude(
                new BigDecimal("30.573000")
        );
        merchant.setEnvironmentTags(
                "[\"quiet\",\"friends\"]"
        );
        merchant.setPlatformStatus("ACTIVE");
        merchant.setOperationStatus("OPERATING");

        return merchant;
    }

    private ConstraintState createBaseConstraints() {
        ConstraintState constraints =
                new ConstraintState();

        constraints.setPartySize(4);
        constraints.setPerCapitaBudget(
                new BigDecimal("80")
        );
        constraints.setCuisines(
                List.of("sichuan")
        );
        constraints.setExcludedMerchantTypes(
                List.of("hotpot")
        );
        constraints.setDistanceKm(
                new BigDecimal("3")
        );
        constraints.setMinRating(
                new BigDecimal("4.5")
        );
        constraints.setEnvironmentRequirements(
                List.of("quiet")
        );

        return constraints;
    }

    private RecommendationWeights createDefaultWeights() {
        RecommendationWeights weights =
                new RecommendationWeights();

        weights.setCuisine(
                new BigDecimal("25")
        );
        weights.setRating(
                new BigDecimal("20")
        );
        weights.setPrice(
                new BigDecimal("20")
        );
        weights.setDistance(
                new BigDecimal("15")
        );
        weights.setEnvironment(
                new BigDecimal("10")
        );
        weights.setReputation(
                new BigDecimal("10")
        );

        return weights;
    }
}
