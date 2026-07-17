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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchScoreCalculatorTest {

    private MatchScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MatchScoreCalculator(
                new ObjectMapper()
        );
    }

    /**
     * 验证正常商家能够完成六个维度的评分，
     * 并生成匹配条件、推荐理由和最终得分。
     */
    @Test
    void shouldCalculateExpectedScoreForMatchedMerchant() {
        Merchant merchant = createBaseMerchant();
        ConstraintState constraints = createBaseConstraints();
        RecommendationWeights weights = createDefaultWeights();

        Optional<RecommendationItemVO> optionalResult =
                calculator.calculate(
                        merchant,
                        constraints,
                        weights,
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
                                .compareTo(result.getFinalScore())
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("0.03")
                                .compareTo(result.getDistanceKm())
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
                                .stream()
                                .anyMatch(value ->
                                        value.contains(
                                                "菜系或商家类型匹配：川菜"
                                        )
                                )
                ),
                () -> assertTrue(
                        result.getMatchedConditions()
                                .stream()
                                .anyMatch(value ->
                                        value.contains(
                                                "评分达到要求"
                                        )
                                )
                ),
                () -> assertTrue(
                        result.getMatchedConditions()
                                .stream()
                                .anyMatch(value ->
                                        value.contains(
                                                "环境或场景匹配：安静"
                                        )
                                )
                ),
                () -> assertTrue(
                        result.getRiskNotes().isEmpty()
                ),
                () -> assertTrue(
                        result.getReason()
                                .contains(
                                        "综合匹配分为97.31分"
                                )
                )
        );
    }

    /**
     * 验证超过人均预算的商家被硬过滤，
     * 不会进入推荐结果。
     */
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

    /**
     * 验证命中明确排除类型“火锅”的商家
     * 会被硬过滤。
     */
    @Test
    void shouldFilterMerchantWhenTypeIsExplicitlyExcluded() {
        Merchant merchant = createBaseMerchant();
        merchant.setName("川味火锅店");
        merchant.setCategory("火锅");

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

    /**
     * 验证暂停营业的商家不会进入推荐结果。
     */
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

    /**
     * 距离不是当前规则中的硬过滤条件。
     * 超过期望距离的商家仍然可以参与排序，
     * 但距离得分降低并产生风险提示。
     */
    @Test
    void shouldKeepDistantMerchantAndAddDistanceRisk() {
        Merchant merchant = createBaseMerchant();
        merchant.setName("远郊川菜馆");
        merchant.setLongitude(
                new BigDecimal("104.150000")
        );
        merchant.setLatitude(
                new BigDecimal("30.600000")
        );

        Optional<RecommendationItemVO> optionalResult =
                calculator.calculate(
                        merchant,
                        createBaseConstraints(),
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(optionalResult.isPresent());

        RecommendationItemVO result =
                optionalResult.orElseThrow();

        assertAll(
                () -> assertTrue(
                        result.getDistanceKm()
                                .compareTo(
                                        new BigDecimal("3")
                                ) > 0
                ),
                () -> assertTrue(
                        result.getRiskNotes()
                                .stream()
                                .anyMatch(value ->
                                        value.contains(
                                                "超过期望的3公里"
                                        )
                                )
                ),
                () -> assertTrue(
                        result.getScoreItems()
                                .containsKey("distance")
                ),
                () -> assertFalse(
                        result.getMatchedConditions()
                                .isEmpty()
                )
        );
    }

    /**
     * 菜系不匹配不是硬过滤条件。
     * 商家仍然参与排序，但菜系维度失分，
     * 并产生未命中菜系的风险说明。
     */
    @Test
    void shouldKeepDifferentCuisineAndAddCuisineRisk() {
        Merchant merchant = createBaseMerchant();
        merchant.setName("粤味轩");
        merchant.setCuisine("粤菜");

        Optional<RecommendationItemVO> optionalResult =
                calculator.calculate(
                        merchant,
                        createBaseConstraints(),
                        createDefaultWeights(),
                        new BigDecimal("30.5728"),
                        new BigDecimal("104.0668")
                );

        assertTrue(optionalResult.isPresent());

        RecommendationItemVO result =
                optionalResult.orElseThrow();

        assertAll(
                () -> assertTrue(
                        result.getRiskNotes()
                                .stream()
                                .anyMatch(value ->
                                        value.contains(
                                                "未命中用户偏好的菜系或商家类型"
                                        )
                                )
                ),
                () -> assertTrue(
                        result.getScoreItems()
                                .containsKey("cuisine")
                ),
                () -> assertTrue(
                        result.getMatchedConditions()
                                .size() >= 2
                ),
                () -> assertTrue(
                        result.getFinalScore()
                                .compareTo(
                                        new BigDecimal("100")
                                ) < 0
                )
        );
    }

    /**
     * 创建与手工测试中“锦里川味馆”一致的基础商家。
     */
    private Merchant createBaseMerchant() {
        Merchant merchant = new Merchant();

        merchant.setId(2L);
        merchant.setMerchantCode("RANK_TEST_001");
        merchant.setName("锦里川味馆");
        merchant.setCategory("中餐");
        merchant.setCuisine("川菜");
        merchant.setRating(
                new BigDecimal("4.80")
        );
        merchant.setAveragePrice(
                new BigDecimal("68.00")
        );
        merchant.setReviewCount(160);
        merchant.setAddress("测试地址");
        merchant.setLongitude(
                new BigDecimal("104.067000")
        );
        merchant.setLatitude(
                new BigDecimal("30.573000")
        );
        merchant.setEnvironmentTags(
                "[\"安静\",\"朋友聚会\"]"
        );
        merchant.setPlatformStatus("ACTIVE");
        merchant.setOperationStatus("OPERATING");

        return merchant;
    }

    /**
     * 创建与会话1一致的用户约束。
     */
    private ConstraintState createBaseConstraints() {
        ConstraintState constraints =
                new ConstraintState();

        constraints.setPartySize(4);
        constraints.setPerCapitaBudget(
                new BigDecimal("80")
        );
        constraints.setCuisines(
                List.of("川菜")
        );
        constraints.setExcludedMerchantTypes(
                List.of("火锅")
        );
        constraints.setDistanceKm(
                new BigDecimal("3")
        );
        constraints.setMinRating(
                new BigDecimal("4.5")
        );
        constraints.setEnvironmentRequirements(
                List.of("安静")
        );

        return constraints;
    }

    /**
     * 创建默认的100分制权重。
     */
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