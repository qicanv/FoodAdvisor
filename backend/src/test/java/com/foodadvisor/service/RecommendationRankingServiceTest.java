package com.foodadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.RecommendationItemVO;
import com.foodadvisor.dto.recommendation.RecommendationRankRequest;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import com.foodadvisor.dto.recommendation.RecommendationWeights;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.entity.ChatSessionState;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Recommendation;
import com.foodadvisor.entity.RecommendationItem;
import com.foodadvisor.mapper.ChatSessionMapper;
import com.foodadvisor.mapper.ChatSessionStateMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.RecommendationItemMapper;
import com.foodadvisor.mapper.RecommendationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationRankingServiceTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private ChatSessionStateMapper chatSessionStateMapper;

    @Mock
    private MerchantMapper merchantMapper;

    @Mock
    private RecommendationMapper recommendationMapper;

    @Mock
    private RecommendationItemMapper recommendationItemMapper;

    @Mock
    private MatchScoreCalculator matchScoreCalculator;

    private RecommendationRankingService service;

    private AtomicLong recommendationIdSequence;

    @BeforeEach
    void setUp() {
        recommendationIdSequence =
                new AtomicLong(1000L);

        service = new RecommendationRankingService(
                chatSessionMapper,
                chatSessionStateMapper,
                merchantMapper,
                recommendationMapper,
                recommendationItemMapper,
                matchScoreCalculator,
                new ObjectMapper()
        );

        /*
         * 模拟数据库插入推荐主记录后，
         * MyBatis-Plus 将自增主键回填到实体中。
         */
        when(recommendationMapper.insert(
                any(Recommendation.class)
        )).thenAnswer(invocation -> {
            Recommendation recommendation =
                    invocation.getArgument(0);

            recommendation.setId(
                    recommendationIdSequence
                            .getAndIncrement()
            );

            return 1;
        });

        when(recommendationMapper.updateById(
                any(Recommendation.class)
        )).thenReturn(1);

        when(recommendationItemMapper.insert(
                any(RecommendationItem.class)
        )).thenReturn(1);
    }

    /**
     * 验证：
     * 1. 商家按照最终得分降序排列；
     * 2. rankNo 从1开始；
     * 3. 接口0～100分被转换为数据库0～1分；
     * 4. 推荐主记录最终更新为SUCCESS。
     */
    @Test
    void shouldSortByFinalScoreAndPersistNormalizedScores() {
        Merchant low = createMerchant(
                101L,
                "低分商家",
                new BigDecimal("4.60"),
                80
        );

        Merchant high = createMerchant(
                102L,
                "高分商家",
                new BigDecimal("4.80"),
                160
        );

        Merchant middle = createMerchant(
                103L,
                "中分商家",
                new BigDecimal("4.70"),
                120
        );

        stubSessionStateAndMerchants(
                List.of(low, high, middle)
        );

        when(matchScoreCalculator.calculate(
                any(Merchant.class),
                any(ConstraintState.class),
                any(RecommendationWeights.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenAnswer(invocation -> {
            Merchant merchant =
                    invocation.getArgument(0);

            BigDecimal score;

            if (merchant.getId().equals(102L)) {
                score = new BigDecimal("95.00");
            } else if (merchant.getId().equals(103L)) {
                score = new BigDecimal("90.00");
            } else {
                score = new BigDecimal("80.00");
            }

            return Optional.of(
                    createCalculatedResult(
                            merchant,
                            score
                    )
            );
        });

        RecommendationRankResponse response =
                service.rank(
                        1L,
                        createRequest(
                                createDefaultWeights()
                        )
                );

        List<Long> orderedMerchantIds =
                response.getResults()
                        .stream()
                        .map(
                                RecommendationItemVO
                                        ::getMerchantId
                        )
                        .toList();

        List<Integer> rankNumbers =
                response.getResults()
                        .stream()
                        .map(
                                RecommendationItemVO
                                        ::getRankNo
                        )
                        .toList();

        assertAll(
                () -> assertEquals(
                        "RULE_V1",
                        response.getAlgorithmVersion()
                ),
                () -> assertEquals(
                        3,
                        response.getResultCount()
                ),
                () -> assertEquals(
                        List.of(
                                102L,
                                103L,
                                101L
                        ),
                        orderedMerchantIds
                ),
                () -> assertEquals(
                        List.of(1, 2, 3),
                        rankNumbers
                )
        );

        /*
         * 检查保存到 recommendation_items 的顺序和分数。
         */
        ArgumentCaptor<RecommendationItem>
                itemCaptor =
                ArgumentCaptor.forClass(
                        RecommendationItem.class
                );

        verify(
                recommendationItemMapper,
                times(3)
        ).insert(itemCaptor.capture());

        List<RecommendationItem> insertedItems =
                itemCaptor.getAllValues();

        assertAll(
                () -> assertEquals(
                        102L,
                        insertedItems.get(0)
                                .getMerchantId()
                ),
                () -> assertEquals(
                        1,
                        insertedItems.get(0)
                                .getRankNo()
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("0.950000")
                                .compareTo(
                                        insertedItems
                                                .get(0)
                                                .getScore()
                                )
                ),
                () -> assertEquals(
                        103L,
                        insertedItems.get(1)
                                .getMerchantId()
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("0.900000")
                                .compareTo(
                                        insertedItems
                                                .get(1)
                                                .getScore()
                                )
                ),
                () -> assertEquals(
                        101L,
                        insertedItems.get(2)
                                .getMerchantId()
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("0.800000")
                                .compareTo(
                                        insertedItems
                                                .get(2)
                                                .getScore()
                                )
                )
        );

        /*
         * 检查推荐主记录最终状态。
         */
        ArgumentCaptor<Recommendation>
                recommendationCaptor =
                ArgumentCaptor.forClass(
                        Recommendation.class
                );

        verify(
                recommendationMapper
        ).updateById(
                recommendationCaptor.capture()
        );

        Recommendation updatedRecommendation =
                recommendationCaptor.getValue();

        assertAll(
                () -> assertEquals(
                        "SUCCESS",
                        updatedRecommendation.getStatus()
                ),
                () -> assertEquals(
                        3,
                        updatedRecommendation
                                .getResultCount()
                ),
                () -> assertEquals(
                        "RULE_V1",
                        updatedRecommendation
                                .getAlgorithmVersion()
                ),
                () -> assertTrue(
                        updatedRecommendation
                                .getWeightSnapshot()
                                .contains(
                                        "\"cuisine\":25"
                                )
                ),
                () -> assertTrue(
                        updatedRecommendation
                                .getParsedConstraints()
                                .contains(
                                        "\"cuisines\""
                                )
                )
        );
    }

    /**
     * 验证同分时按照以下顺序稳定排序：
     *
     * 1. 商家评分降序；
     * 2. 评论数量降序；
     * 3. 商家ID升序。
     *
     * 连续执行三次，结果必须一致。
     */
    @Test
    void shouldProduceSameOrderThreeTimesWhenScoresTie() {
        Merchant merchant4 = createMerchant(
                4L,
                "评分较低但评论多",
                new BigDecimal("4.60"),
                200
        );

        Merchant merchant2 = createMerchant(
                2L,
                "高评分评论较少",
                new BigDecimal("4.80"),
                50
        );

        Merchant merchant3 = createMerchant(
                3L,
                "高评分高评论ID较大",
                new BigDecimal("4.80"),
                120
        );

        Merchant merchant1 = createMerchant(
                1L,
                "高评分高评论ID较小",
                new BigDecimal("4.80"),
                120
        );

        /*
         * 故意使用无序列表，
         * 验证最终结果不依赖数据库返回顺序。
         */
        stubSessionStateAndMerchants(
                List.of(
                        merchant4,
                        merchant2,
                        merchant3,
                        merchant1
                )
        );

        when(matchScoreCalculator.calculate(
                any(Merchant.class),
                any(ConstraintState.class),
                any(RecommendationWeights.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenAnswer(invocation -> {
            Merchant merchant =
                    invocation.getArgument(0);

            return Optional.of(
                    createCalculatedResult(
                            merchant,
                            new BigDecimal("88.00")
                    )
            );
        });

        List<List<Long>> threeRankings =
                new ArrayList<>();

        for (int index = 0;
             index < 3;
             index++) {

            RecommendationRankResponse response =
                    service.rank(
                            1L,
                            createRequest(
                                    createDefaultWeights()
                            )
                    );

            threeRankings.add(
                    response.getResults()
                            .stream()
                            .map(
                                    RecommendationItemVO
                                            ::getMerchantId
                            )
                            .toList()
            );
        }

        List<Long> expectedOrder =
                List.of(
                        1L,
                        3L,
                        2L,
                        4L
                );

        assertAll(
                () -> assertEquals(
                        expectedOrder,
                        threeRankings.get(0)
                ),
                () -> assertEquals(
                        expectedOrder,
                        threeRankings.get(1)
                ),
                () -> assertEquals(
                        expectedOrder,
                        threeRankings.get(2)
                ),
                () -> assertEquals(
                        threeRankings.get(0),
                        threeRankings.get(1)
                ),
                () -> assertEquals(
                        threeRankings.get(1),
                        threeRankings.get(2)
                )
        );

        verify(
                recommendationMapper,
                times(3)
        ).insert(
                any(Recommendation.class)
        );

        verify(
                recommendationItemMapper,
                times(12)
        ).insert(
                any(RecommendationItem.class)
        );
    }

    /**
     * 验证修改权重后：
     *
     * 1. Service 将新权重传入评分器；
     * 2. 新权重能够导致排名发生变化；
     * 3. 两次请求保存不同的权重快照；
     * 4. 两次请求生成不同的推荐记录。
     */
    @Test
    void shouldChangeOrderAndWeightSnapshotWhenWeightsChange() {
        Merchant nearbyMerchant =
                createMerchant(
                        201L,
                        "近距离商家",
                        new BigDecimal("4.70"),
                        100
                );

        Merchant cuisineMerchant =
                createMerchant(
                        202L,
                        "菜系匹配商家",
                        new BigDecimal("4.70"),
                        100
                );

        stubSessionStateAndMerchants(
                List.of(
                        nearbyMerchant,
                        cuisineMerchant
                )
        );

        when(matchScoreCalculator.calculate(
                any(Merchant.class),
                any(ConstraintState.class),
                any(RecommendationWeights.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenAnswer(invocation -> {
            Merchant merchant =
                    invocation.getArgument(0);

            RecommendationWeights weights =
                    invocation.getArgument(2);

            boolean distanceWeightIncreased =
                    weights.getDistance()
                            .compareTo(
                                    new BigDecimal("30")
                            ) == 0;

            BigDecimal score;

            if (merchant.getId().equals(201L)) {
                /*
                 * 距离权重提高后，
                 * 近距离商家的得分上升。
                 */
                score = distanceWeightIncreased
                        ? new BigDecimal("90.00")
                        : new BigDecimal("70.00");
            } else {
                /*
                 * 菜系权重降低后，
                 * 菜系匹配商家的优势下降。
                 */
                score = distanceWeightIncreased
                        ? new BigDecimal("65.00")
                        : new BigDecimal("85.00");
            }

            return Optional.of(
                    createCalculatedResult(
                            merchant,
                            score
                    )
            );
        });

        RecommendationRankResponse
                defaultWeightResponse =
                service.rank(
                        1L,
                        createRequest(
                                createDefaultWeights()
                        )
                );

        RecommendationRankResponse
                changedWeightResponse =
                service.rank(
                        1L,
                        createRequest(
                                createDistanceFocusedWeights()
                        )
                );

        List<Long> defaultOrder =
                defaultWeightResponse.getResults()
                        .stream()
                        .map(
                                RecommendationItemVO
                                        ::getMerchantId
                        )
                        .toList();

        List<Long> changedOrder =
                changedWeightResponse.getResults()
                        .stream()
                        .map(
                                RecommendationItemVO
                                        ::getMerchantId
                        )
                        .toList();

        assertAll(
                () -> assertEquals(
                        List.of(202L, 201L),
                        defaultOrder
                ),
                () -> assertEquals(
                        List.of(201L, 202L),
                        changedOrder
                ),
                () -> assertNotEquals(
                        defaultOrder,
                        changedOrder
                ),
                () -> assertNotEquals(
                        defaultWeightResponse
                                .getRecommendationId(),
                        changedWeightResponse
                                .getRecommendationId()
                )
        );

        ArgumentCaptor<Recommendation>
                recommendationCaptor =
                ArgumentCaptor.forClass(
                        Recommendation.class
                );

        verify(
                recommendationMapper,
                times(2)
        ).updateById(
                recommendationCaptor.capture()
        );

        List<Recommendation>
                savedRecommendations =
                recommendationCaptor.getAllValues();

        String defaultSnapshot =
                savedRecommendations.get(0)
                        .getWeightSnapshot();

        String changedSnapshot =
                savedRecommendations.get(1)
                        .getWeightSnapshot();

        assertAll(
                () -> assertTrue(
                        defaultSnapshot.contains(
                                "\"cuisine\":25"
                        )
                ),
                () -> assertTrue(
                        defaultSnapshot.contains(
                                "\"distance\":15"
                        )
                ),
                () -> assertTrue(
                        changedSnapshot.contains(
                                "\"cuisine\":10"
                        )
                ),
                () -> assertTrue(
                        changedSnapshot.contains(
                                "\"distance\":30"
                        )
                ),
                () -> assertNotEquals(
                        defaultSnapshot,
                        changedSnapshot
                )
        );
    }

    /**
     * 模拟有效会话、会话约束和候选商家。
     */
    private void stubSessionStateAndMerchants(
            List<Merchant> merchants
    ) {
        ChatSession session =
                new ChatSession();

        session.setId(1L);
        session.setUserId(1L);
        session.setStatus("ACTIVE");

        ChatSessionState sessionState =
                new ChatSessionState();

        sessionState.setId(1L);
        sessionState.setSessionId(1L);
        sessionState.setConversationStage(
                "SEARCHING"
        );
        sessionState.setPendingConfirmation(
                "[]"
        );
        sessionState.setCurrentConstraints(
                """
                {
                  "partySize": 4,
                  "perCapitaBudget": 80,
                  "cuisines": ["川菜"],
                  "excludedMerchantTypes": ["火锅"],
                  "distanceKm": 3,
                  "minRating": 4.5,
                  "environmentRequirements": ["安静"]
                }
                """
        );

        when(chatSessionMapper.selectById(1L))
                .thenReturn(session);

        when(chatSessionStateMapper.selectOne(
                any()
        )).thenReturn(sessionState);

        when(merchantMapper.selectList(
                any()
        )).thenReturn(merchants);
    }

    /**
     * 创建推荐请求。
     */
    private RecommendationRankRequest createRequest(
            RecommendationWeights weights
    ) {
        RecommendationRankRequest request =
                new RecommendationRankRequest();

        request.setUserId(1L);

        request.setUserLatitude(
                new BigDecimal("30.5728")
        );

        request.setUserLongitude(
                new BigDecimal("104.0668")
        );

        request.setWeights(weights);

        return request;
    }

    /**
     * 默认权重：
     * 25 + 20 + 20 + 15 + 10 + 10 = 100。
     */
    private RecommendationWeights
    createDefaultWeights() {
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

    /**
     * 调高距离和评分权重，
     * 降低菜系和价格权重。
     *
     * 10 + 30 + 10 + 30 + 10 + 10 = 100。
     */
    private RecommendationWeights
    createDistanceFocusedWeights() {
        RecommendationWeights weights =
                new RecommendationWeights();

        weights.setCuisine(
                new BigDecimal("10")
        );
        weights.setRating(
                new BigDecimal("30")
        );
        weights.setPrice(
                new BigDecimal("10")
        );
        weights.setDistance(
                new BigDecimal("30")
        );
        weights.setEnvironment(
                new BigDecimal("10")
        );
        weights.setReputation(
                new BigDecimal("10")
        );

        return weights;
    }

    /**
     * 创建候选商家。
     */
    private Merchant createMerchant(
            Long id,
            String name,
            BigDecimal rating,
            Integer reviewCount
    ) {
        Merchant merchant =
                new Merchant();

        merchant.setId(id);
        merchant.setMerchantCode(
                "TEST_" + id
        );
        merchant.setName(name);
        merchant.setCategory("中餐");
        merchant.setCuisine("川菜");
        merchant.setRating(rating);
        merchant.setAveragePrice(
                new BigDecimal("68.00")
        );
        merchant.setReviewCount(reviewCount);
        merchant.setAddress("测试地址");
        merchant.setLongitude(
                new BigDecimal("104.067000")
        );
        merchant.setLatitude(
                new BigDecimal("30.573000")
        );
        merchant.setEnvironmentTags(
                "[\"安静\"]"
        );
        merchant.setPlatformStatus("ACTIVE");
        merchant.setOperationStatus(
                "OPERATING"
        );

        return merchant;
    }

    /**
     * 模拟评分器产生的结果。
     *
     * MatchScoreCalculator 自身的计算规则，
     * 已经由 MatchScoreCalculatorTest 单独验证。
     * 当前测试只验证 RankingService 的排序、
     * 排名编号、持久化和权重快照。
     */
    private RecommendationItemVO
    createCalculatedResult(
            Merchant merchant,
            BigDecimal finalScore
    ) {
        RecommendationItemVO result =
                new RecommendationItemVO();

        result.setMerchantId(
                merchant.getId()
        );
        result.setMerchantName(
                merchant.getName()
        );
        result.setCategory(
                merchant.getCategory()
        );
        result.setCuisine(
                merchant.getCuisine()
        );
        result.setMerchantRating(
                merchant.getRating()
        );
        result.setAveragePrice(
                merchant.getAveragePrice()
        );
        result.setReviewCount(
                merchant.getReviewCount()
        );
        result.setDistanceKm(
                new BigDecimal("1.00")
        );
        result.setFinalScore(
                finalScore
        );

        result.getMatchedConditions()
                .add("测试匹配条件一");

        result.getMatchedConditions()
                .add("测试匹配条件二");

        result.setReason(
                merchant.getName()
                        + "综合匹配分为"
                        + finalScore
                        + "分。"
        );

        return result;
    }
}