package com.foodadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.AdjustmentSuggestionVO;
import com.foodadvisor.dto.recommendation.RecommendationAdjustRequest;
import com.foodadvisor.dto.recommendation.RecommendationItemVO;
import com.foodadvisor.dto.recommendation.RecommendationRankRequest;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import com.foodadvisor.dto.recommendation.RecommendationWeights;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.entity.ChatSessionState;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Recommendation;
import com.foodadvisor.entity.RecommendationItem;
import com.foodadvisor.entity.Dish;
import com.foodadvisor.mapper.ChatSessionMapper;
import com.foodadvisor.mapper.ChatSessionStateMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.RecommendationItemMapper;
import com.foodadvisor.mapper.RecommendationMapper;
import com.foodadvisor.mapper.DishMapper;
import com.foodadvisor.mapper.RecommendationEvidenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    private DishMapper dishMapper;

    @Mock
    private RecommendationEvidenceMapper recommendationEvidenceMapper;

    private DishMatchingService dishMatchingService;

    @Mock
    private MatchScoreCalculator matchScoreCalculator;

    @Mock
    private MerchantBusinessHoursService businessHoursService;

    private RecommendationRankingService service;

    private AtomicLong recommendationIdSequence;

    @BeforeEach
    void setUp() {
        recommendationIdSequence =
                new AtomicLong(1000L);
        dishMatchingService =
                new DishMatchingService(new ObjectMapper());

        service = new RecommendationRankingService(
                chatSessionMapper,
                chatSessionStateMapper,
                merchantMapper,
                recommendationMapper,
                recommendationItemMapper,
                dishMapper,
                recommendationEvidenceMapper,
                dishMatchingService,
                matchScoreCalculator,
                businessHoursService,
                new ObjectMapper()
        );

        lenient().when(businessHoursService.hasBusinessTimeConstraint(any()))
                .thenReturn(false);
        lenient().when(businessHoursService.match(any(), any()))
                .thenReturn(
                        new MerchantBusinessHoursService.BusinessHoursMatch(
                                true,
                                null
                        )
                );

        lenient().when(recommendationMapper.insert(
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

        lenient().when(recommendationMapper.updateById(
                any(Recommendation.class)
        )).thenReturn(1);

        lenient().when(recommendationItemMapper.insert(
                any(RecommendationItem.class)
        )).thenAnswer(invocation -> {
            RecommendationItem item = invocation.getArgument(0);
            item.setId(2000L + item.getMerchantId());
            return 1;
        });

        lenient().when(recommendationEvidenceMapper.insert(
                any(com.foodadvisor.entity.RecommendationEvidence.class)
        ))
                .thenReturn(1);

        lenient().when(chatSessionStateMapper.updateById(
                any(ChatSessionState.class)
        )).thenReturn(1);

        lenient().when(matchScoreCalculator
                .resolvePerCapitaBudget(
                        any(ConstraintState.class)
                )).thenAnswer(invocation -> {
            ConstraintState constraints =
                    invocation.getArgument(0);

            if (constraints.getPerCapitaBudget() != null) {
                return constraints.getPerCapitaBudget();
            }

            if (constraints.getTotalBudget() != null
                    && constraints.getPartySize() != null
                    && constraints.getPartySize() > 0) {
                return constraints.getTotalBudget()
                        .divide(
                                new BigDecimal(
                                        constraints
                                                .getPartySize()
                                ),
                                2,
                                java.math.RoundingMode.HALF_UP
                        );
            }

            return null;
        });
    }

    @Test
    void shouldSortByFinalScoreAndPersistNormalizedScores() {
        Merchant low = createMerchant(
                101L,
                "low",
                new BigDecimal("4.60"),
                80
        );
        Merchant high = createMerchant(
                102L,
                "high",
                new BigDecimal("4.80"),
                160
        );
        Merchant middle = createMerchant(
                103L,
                "middle",
                new BigDecimal("4.70"),
                120
        );

        stubSessionStateAndMerchants(
                List.of(low, high, middle),
                defaultConstraintsJson()
        );
        stubCalculatedScores();

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

        assertAll(
                () -> assertEquals(
                        "RULE_V1",
                        response.getAlgorithmVersion()
                ),
                () -> assertTrue(response.getMatched()),
                () -> assertEquals("SUCCESS", response.getStatus()),
                () -> assertEquals(3, response.getResultCount()),
                () -> assertEquals(
                        List.of(102L, 103L, 101L),
                        orderedMerchantIds
                )
        );

        ArgumentCaptor<RecommendationItem> itemCaptor =
                ArgumentCaptor.forClass(
                        RecommendationItem.class
                );
        verify(
                recommendationItemMapper,
                times(3)
        ).insert(itemCaptor.capture());

        assertEquals(
                0,
                new BigDecimal("0.950000")
                        .compareTo(
                                itemCaptor.getAllValues()
                                        .get(0)
                                        .getScore()
                        )
        );
        assertTrue(
                itemCaptor.getAllValues()
                        .get(0)
                        .getScoreDetails()
                        .contains("\"distanceKm\":1.00")
        );
    }

    @Test
    void dishKeywordsUseOneBatchQueryAndFilterUnmatchedMerchants() {
        Merchant matched = createMerchant(
                101L, "matched", new BigDecimal("4.8"), 20
        );
        Merchant unmatched = createMerchant(
                102L, "unmatched", new BigDecimal("4.7"), 20
        );
        stubSessionStateAndMerchants(
                List.of(matched, unmatched),
                """
                {"dishKeywords":["水煮鱼"]}
                """
        );
        stubCalculatedScores();
        Dish dish = new Dish();
        dish.setId(501L);
        dish.setMerchantId(101L);
        dish.setName("招牌水煮鱼");
        dish.setPrice(new BigDecimal("68"));
        dish.setTasteTags("[]");
        when(dishMapper.selectActiveByMerchantIds(any()))
                .thenReturn(List.of(dish));

        RecommendationRankResponse response =
                service.rank(
                        1L,
                        createRequest(createDefaultWeights())
                );

        assertEquals("SUCCESS", response.getStatus());
        assertEquals(1, response.getResults().size());
        assertEquals(
                "招牌水煮鱼",
                response.getResults().get(0)
                        .getMatchedDishes().get(0).getDishName()
        );
        verify(dishMapper, times(1))
                .selectActiveByMerchantIds(any());
        verify(recommendationEvidenceMapper, times(1))
                .insert(any(
                        com.foodadvisor.entity
                                .RecommendationEvidence.class
                ));
    }

    @Test
    void noDishKeywordsDoNotQueryDishes() {
        Merchant merchant = createMerchant(
                101L, "ordinary", new BigDecimal("4.8"), 20
        );
        stubSessionStateAndMerchants(
                List.of(merchant),
                "{}"
        );
        stubCalculatedScores();

        service.rank(1L, createRequest(createDefaultWeights()));

        verify(dishMapper, never())
                .selectActiveByMerchantIds(any());
    }

    @Test
    void shouldBatchLoadAndHardFilterByBusinessHours() {
        Merchant open = createMerchant(
                201L, "open", new BigDecimal("4.8"), 100
        );
        Merchant closed = createMerchant(
                202L, "closed", new BigDecimal("4.7"), 90
        );
        stubSessionStateAndMerchants(
                List.of(open, closed),
                "{\"businessTime\":\"NOW_OPEN\"}"
        );
        when(businessHoursService.hasBusinessTimeConstraint(any()))
                .thenReturn(true);
        when(businessHoursService.loadGrouped(
                eq(List.of(201L, 202L))
        )).thenReturn(Map.of(201L, List.of(), 202L, List.of()));
        when(businessHoursService.match(
                any(),
                eq(List.of())
        )).thenReturn(
                new MerchantBusinessHoursService.BusinessHoursMatch(
                        true,
                        "当前处于营业时段 10:00–22:00"
                ),
                new MerchantBusinessHoursService.BusinessHoursMatch(
                        false,
                        null
                )
        );
        when(matchScoreCalculator.calculate(
                eq(open),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(Optional.of(
                createCalculatedResult(open, new BigDecimal("90"))
        ));

        RecommendationRankResponse response = service.rank(
                1L,
                createRequest(createDefaultWeights())
        );

        assertEquals(List.of(201L), response.getResults().stream()
                .map(RecommendationItemVO::getMerchantId)
                .toList());
        verify(businessHoursService, times(1))
                .loadGrouped(eq(List.of(201L, 202L)));
        verify(matchScoreCalculator, never()).calculate(
                eq(closed), any(), any(), any(), any()
        );
        verify(matchScoreCalculator).addBusinessHoursEvidence(
                any(),
                eq("当前处于营业时段 10:00–22:00")
        );
    }

    @Test
    void shouldNotLoadBusinessHoursWithoutConstraint() {
        Merchant merchant = createMerchant(
                203L, "normal", new BigDecimal("4.8"), 100
        );
        stubSessionStateAndMerchants(List.of(merchant), "{}");
        when(matchScoreCalculator.calculate(
                eq(merchant), any(), any(), any(), any()
        )).thenReturn(Optional.of(
                createCalculatedResult(merchant, new BigDecimal("90"))
        ));

        service.rank(1L, createRequest(createDefaultWeights()));

        verify(businessHoursService, never()).loadGrouped(any());
    }

    @Test
    void shouldProduceSameOrderThreeTimesWhenScoresTie() {
        Merchant merchant4 = createMerchant(
                4L,
                "lower rating many reviews",
                new BigDecimal("4.60"),
                200
        );
        Merchant merchant2 = createMerchant(
                2L,
                "high rating fewer reviews",
                new BigDecimal("4.80"),
                50
        );
        Merchant merchant3 = createMerchant(
                3L,
                "high rating high reviews id3",
                new BigDecimal("4.80"),
                120
        );
        Merchant merchant1 = createMerchant(
                1L,
                "high rating high reviews id1",
                new BigDecimal("4.80"),
                120
        );

        stubSessionStateAndMerchants(
                List.of(
                        merchant4,
                        merchant2,
                        merchant3,
                        merchant1
                ),
                defaultConstraintsJson()
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

        for (int index = 0; index < 3; index++) {
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
                List.of(1L, 3L, 2L, 4L);

        assertAll(
                () -> assertEquals(
                        expectedOrder,
                        threeRankings.get(0)
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
    }

    @Test
    void shouldChangeOrderAndWeightSnapshotWhenWeightsChange() {
        Merchant nearbyMerchant = createMerchant(
                201L,
                "nearby",
                new BigDecimal("4.70"),
                100
        );
        Merchant cuisineMerchant = createMerchant(
                202L,
                "cuisine",
                new BigDecimal("4.70"),
                100
        );

        stubSessionStateAndMerchants(
                List.of(nearbyMerchant, cuisineMerchant),
                defaultConstraintsJson()
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
                score = distanceWeightIncreased
                        ? new BigDecimal("90.00")
                        : new BigDecimal("70.00");
            } else {
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

        RecommendationRankResponse defaultWeightResponse =
                service.rank(
                        1L,
                        createRequest(
                                createDefaultWeights()
                        )
                );

        RecommendationRankResponse changedWeightResponse =
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
                        defaultWeightResponse
                                .getRecommendationId(),
                        changedWeightResponse
                                .getRecommendationId()
                )
        );
    }

    @Test
    void shouldReturnNoMatchAndBudgetSuggestion() {
        Merchant candidate = createMerchant(
                301L,
                "budget candidate",
                new BigDecimal("4.8"),
                20
        );
        candidate.setAveragePrice(
                new BigDecimal("90")
        );

        stubSessionStateAndMerchants(
                List.of(candidate),
                """
                {
                  "perCapitaBudget": 50,
                  "cuisines": ["sichuan"]
                }
                """
        );
        stubNoCalculatedResult();
        when(matchScoreCalculator.passesHardFilters(
                any(Merchant.class),
                any(ConstraintState.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenAnswer(invocation -> {
            ConstraintState constraints =
                    invocation.getArgument(1);
            return constraints.getPerCapitaBudget() == null;
        });

        RecommendationRankResponse response =
                service.rank(
                        1L,
                        createRequest(
                                createDefaultWeights()
                        )
                );

        assertAll(
                () -> assertFalse(response.getMatched()),
                () -> assertEquals("NO_MATCH", response.getStatus()),
                () -> assertEquals(0, response.getResultCount()),
                () -> assertTrue(response.getResults().isEmpty()),
                () -> assertEquals(
                        "当前没有完全匹配的结果",
                        response.getMessage()
                ),
                () -> assertTrue(
                        response.getAdjustmentSuggestions()
                                .stream()
                                .anyMatch(suggestion ->
                                        "INCREASE_BUDGET"
                                                .equals(
                                                        suggestion
                                                                .getType()
                                                )
                                )
                )
        );
    }

    @Test
    void shouldReturnDistanceSuggestionWhenDistanceTooSmall() {
        Merchant candidate = createMerchant(
                302L,
                "distance candidate",
                new BigDecimal("4.8"),
                20
        );

        stubSessionStateAndMerchants(
                List.of(candidate),
                """
                {
                  "distanceKm": 1,
                  "cuisines": ["sichuan"]
                }
                """
        );
        stubNoCalculatedResult();
        when(matchScoreCalculator.passesHardFilters(
                any(Merchant.class),
                any(ConstraintState.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenAnswer(invocation -> {
            ConstraintState constraints =
                    invocation.getArgument(1);
            return constraints.getDistanceKm() == null
                    || constraints.getDistanceKm()
                    .compareTo(new BigDecimal("3")) >= 0;
        });
        when(matchScoreCalculator.calculateDistanceKm(
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenReturn(new BigDecimal("2.3"));

        RecommendationRankResponse response =
                service.rank(
                        1L,
                        createRequest(
                                createDefaultWeights()
                        )
                );

        assertAll(
                () -> assertFalse(response.getMatched()),
                () -> assertTrue(
                        response.getAdjustmentSuggestions()
                                .stream()
                                .anyMatch(suggestion ->
                                        "EXPAND_DISTANCE"
                                                .equals(
                                                        suggestion
                                                                .getType()
                                                )
                                )
                )
        );
    }

    @Test
    void shouldReturnCuisineSuggestionWhenCuisineDoesNotMatch() {
        Merchant candidate = createMerchant(
                303L,
                "other cuisine",
                new BigDecimal("4.8"),
                20
        );
        candidate.setCuisine("cantonese");

        stubSessionStateAndMerchants(
                List.of(candidate),
                """
                {
                  "cuisines": ["sichuan"]
                }
                """
        );
        stubNoCalculatedResult();
        when(matchScoreCalculator.passesHardFilters(
                any(Merchant.class),
                any(ConstraintState.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenAnswer(invocation -> {
            ConstraintState constraints =
                    invocation.getArgument(1);
            return constraints.getCuisines() == null
                    || constraints.getCuisines().isEmpty();
        });

        RecommendationRankResponse response =
                service.rank(
                        1L,
                        createRequest(
                                createDefaultWeights()
                        )
                );

        assertTrue(
                response.getAdjustmentSuggestions()
                        .stream()
                        .anyMatch(suggestion ->
                                "RELAX_CUISINE"
                                        .equals(
                                                suggestion
                                                        .getType()
                                        )
                        )
        );
    }

    @Test
    void shouldUpdateConstraintsAndRerankAfterBudgetAdjustment() {
        ChatSessionState sessionState =
                stubSessionStateAndMerchants(
                        List.of(createMerchant(
                                401L,
                                "adjusted",
                                new BigDecimal("4.8"),
                                20
                        )),
                        """
                        {
                          "perCapitaBudget": 50,
                          "cuisines": ["sichuan"]
                        }
                        """
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
            ConstraintState constraints =
                    invocation.getArgument(1);

            if (constraints.getPerCapitaBudget()
                    .compareTo(
                            new BigDecimal("90")
                    ) >= 0) {
                return Optional.of(
                        createCalculatedResult(
                                merchant,
                                new BigDecimal("91")
                        )
                );
            }

            return Optional.empty();
        });

        RecommendationAdjustRequest request =
                new RecommendationAdjustRequest();
        request.setUserId(1L);
        request.setField("perCapitaBudget");
        request.setValue(new BigDecimal("90"));
        request.setUserLatitude(new BigDecimal("30.5728"));
        request.setUserLongitude(new BigDecimal("104.0668"));

        RecommendationRankResponse response =
                service.adjustAndRank(1L, request);

        assertAll(
                () -> assertTrue(response.getMatched()),
                () -> assertEquals(1, response.getResultCount()),
                () -> assertTrue(
                        sessionState.getCurrentConstraints()
                                .contains(
                                        "\"perCapitaBudget\":90"
                                )
                )
        );
    }

    @Test
    void shouldOnlyAdjustDistanceAndKeepOtherConstraints() {
        ChatSessionState sessionState =
                stubSessionStateAndMerchants(
                        List.of(createMerchant(
                                402L,
                                "distance adjusted",
                                new BigDecimal("4.8"),
                                20
                        )),
                        """
                        {
                          "distanceKm": 1,
                          "cuisines": ["sichuan"]
                        }
                        """
                );
        stubCalculatedScores();

        RecommendationAdjustRequest request =
                new RecommendationAdjustRequest();
        request.setUserId(1L);
        request.setField("distanceKm");
        request.setValue(new BigDecimal("5"));
        request.setUserLatitude(new BigDecimal("30.5728"));
        request.setUserLongitude(new BigDecimal("104.0668"));

        service.adjustAndRank(1L, request);

        assertAll(
                () -> assertTrue(
                        sessionState.getCurrentConstraints()
                                .contains("\"distanceKm\":5")
                ),
                () -> assertTrue(
                        sessionState.getCurrentConstraints()
                                .contains("\"sichuan\"")
                )
        );
    }

    @Test
    void shouldRejectUnknownAdjustmentField() {
        stubSessionStateAndMerchants(
                List.of(),
                defaultConstraintsJson()
        );

        RecommendationAdjustRequest request =
                new RecommendationAdjustRequest();
        request.setUserId(1L);
        request.setField("unknownField");
        request.setValue("x");

        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> service.adjustAndRank(
                                1L,
                                request
                        )
                );

        assertEquals(
                "INVALID_RECOMMENDATION_ADJUSTMENT",
                exception.getCode()
        );
    }

    @Test
    void shouldRequireLocationWhenDistanceIsSet() {
        stubSessionStateAndMerchants(
                List.of(createMerchant(
                        501L,
                        "needs location",
                        new BigDecimal("4.8"),
                        20
                )),
                """
                {
                  "distanceKm": 3
                }
                """
        );

        RecommendationRankRequest request =
                new RecommendationRankRequest();
        request.setUserId(1L);
        request.setUserLatitude(null);
        request.setUserLongitude(null);
        request.setWeights(createDefaultWeights());

        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> service.rank(1L, request)
                );

        assertEquals(
                "USER_LOCATION_REQUIRED",
                exception.getCode()
        );
    }

    @Test
    void shouldReturnDataServiceErrorWhenMerchantQueryFails() {
        stubSessionStateAndMerchants(
                List.of(),
                """
                {
                  "cuisines": ["sichuan"]
                }
                """
        );
        when(merchantMapper.selectList(any()))
                .thenThrow(
                        new DataRetrievalFailureException(
                                "database unavailable"
                        )
                );

        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> service.rank(
                                1L,
                                createRequest(
                                        createDefaultWeights()
                                )
                        )
                );

        assertEquals(
                "RECOMMENDATION_DATA_SERVICE_ERROR",
                exception.getCode()
        );
    }

    @Test
    void shouldNotGenerateDistanceSuggestionWhenRecoveredMerchantsHaveNoCoordinates() {
        Merchant candidate = createMerchant(
                601L,
                "no coordinate candidate",
                new BigDecimal("4.8"),
                20
        );
        candidate.setLatitude(null);
        candidate.setLongitude(null);

        stubSessionStateAndMerchants(
                List.of(candidate),
                """
                {
                  "distanceKm": 1,
                  "cuisines": ["sichuan"]
                }
                """
        );
        stubNoCalculatedResult();
        when(matchScoreCalculator.passesHardFilters(
                any(Merchant.class),
                any(ConstraintState.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenAnswer(invocation -> {
            ConstraintState constraints =
                    invocation.getArgument(1);
            return constraints.getDistanceKm() == null;
        });

        RecommendationRankResponse response =
                service.rank(
                        1L,
                        createRequest(
                                createDefaultWeights()
                        )
                );

        assertAll(
                () -> assertFalse(
                        response.getAdjustmentSuggestions()
                                .isEmpty()
                ),
                () -> assertFalse(
                        response.getAdjustmentSuggestions()
                                .stream()
                                .anyMatch(suggestion ->
                                        "EXPAND_DISTANCE"
                                                .equals(
                                                        suggestion
                                                                .getType()
                                                )
                                )
                )
        );
    }

    @Test
    void shouldGenerateExecutableDistanceSuggestionFromCoordinateCandidatesOnly() {
        Merchant noCoordinate = createMerchant(
                602L,
                "no coordinate",
                new BigDecimal("4.8"),
                20
        );
        noCoordinate.setLatitude(null);
        noCoordinate.setLongitude(null);

        Merchant withCoordinate = createMerchant(
                603L,
                "with coordinate",
                new BigDecimal("4.8"),
                20
        );

        stubSessionStateAndMerchants(
                List.of(noCoordinate, withCoordinate),
                """
                {
                  "distanceKm": 1
                }
                """
        );
        stubNoCalculatedResult();
        when(matchScoreCalculator.passesHardFilters(
                any(Merchant.class),
                any(ConstraintState.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenAnswer(invocation -> {
            Merchant merchant =
                    invocation.getArgument(0);
            ConstraintState constraints =
                    invocation.getArgument(1);

            if (constraints.getDistanceKm() == null) {
                return true;
            }

            return merchant.getId().equals(603L)
                    && constraints.getDistanceKm()
                    .compareTo(new BigDecimal("3")) >= 0;
        });
        when(matchScoreCalculator.calculateDistanceKm(
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenReturn(new BigDecimal("2.1"));

        RecommendationRankResponse response =
                service.rank(
                        1L,
                        createRequest(
                                createDefaultWeights()
                        )
                );

        AdjustmentSuggestionVO suggestion =
                response.getAdjustmentSuggestions()
                        .stream()
                        .filter(item ->
                                "EXPAND_DISTANCE".equals(
                                        item.getType()
                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertAll(
                () -> assertEquals(
                        new BigDecimal("3"),
                        suggestion.getSuggestedValue()
                ),
                () -> assertTrue(
                        response.getLimitingConditions()
                                .stream()
                                .anyMatch(condition ->
                                        "distanceKm".equals(
                                                condition.getField()
                                        )
                                                && condition
                                                .getRecoveredMerchantCount()
                                                == 1
                                )
                )
        );
    }

    @Test
    void shouldReturnFallbackSuggestionWhenBaseCandidatesAreEmpty() {
        stubSessionStateAndMerchants(
                List.of(),
                "{}"
        );

        RecommendationRankResponse response =
                service.rank(
                        1L,
                        createRequest(
                                createDefaultWeights()
                        )
                );

        assertAll(
                () -> assertFalse(response.getMatched()),
                () -> assertFalse(
                        response.getAdjustmentSuggestions()
                                .isEmpty()
                ),
                () -> assertTrue(
                        List.of(
                                "perCapitaBudget",
                                "totalBudget",
                                "distanceKm",
                                "cuisines",
                                "scenes",
                                "environmentRequirements",
                                "minRating"
                        ).contains(
                                response
                                        .getAdjustmentSuggestions()
                                        .get(0)
                                        .getField()
                        )
                )
        );
    }

    @Test
    void shouldRejectInvalidNumericAdjustments() {
        stubSessionStateAndMerchants(
                List.of(),
                "{}"
        );

        assertInvalidAdjustment(
                "perCapitaBudget",
                BigDecimal.ZERO
        );
        assertInvalidAdjustment(
                "perCapitaBudget",
                new BigDecimal("-1")
        );
        assertInvalidAdjustment(
                "distanceKm",
                BigDecimal.ZERO
        );
        assertInvalidAdjustment(
                "distanceKm",
                new BigDecimal("101")
        );
        assertInvalidAdjustment(
                "minRating",
                new BigDecimal("-0.1")
        );
        assertInvalidAdjustment(
                "minRating",
                new BigDecimal("5.1")
        );
        assertInvalidAdjustment(
                "perCapitaBudget",
                Double.NaN
        );
        assertInvalidAdjustment(
                "perCapitaBudget",
                Double.POSITIVE_INFINITY
        );
        assertInvalidAdjustment(
                "perCapitaBudget",
                "not-a-number"
        );
    }

    @Test
    void shouldRejectInvalidListAdjustments() {
        stubSessionStateAndMerchants(
                List.of(),
                "{}"
        );

        assertInvalidAdjustment(
                "cuisines",
                List.of(1)
        );
        assertInvalidAdjustment(
                "cuisines",
                List.of(new Object())
        );

        List<String> tooMany =
                new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            tooMany.add("tag" + index);
        }
        assertInvalidAdjustment("cuisines", tooMany);
        assertInvalidAdjustment(
                "cuisines",
                List.of("abcdefghijklmnopqrstuvwxyzabcde")
        );
        assertInvalidAdjustment(
                "cuisines",
                List.of(" ", "　")
        );

        List<Object> containsNull =
                new ArrayList<>();
        containsNull.add(null);
        assertInvalidAdjustment("cuisines", containsNull);
    }

    @Test
    void shouldTrimDeduplicateAndClearListAdjustments() {
        ChatSessionState sessionState =
                stubSessionStateAndMerchants(
                        List.of(createMerchant(
                                604L,
                                "list adjusted",
                                new BigDecimal("4.8"),
                                20
                        )),
                        """
                        {
                          "cuisines": ["sichuan"]
                        }
                        """
                );
        stubCalculatedScores();

        RecommendationAdjustRequest request =
                createAdjustRequest(
                        "cuisines",
                        List.of(
                                " sichuan ",
                                "sichuan",
                                " cantonese "
                        )
                );

        service.adjustAndRank(1L, request);

        assertTrue(
                sessionState.getCurrentConstraints()
                        .contains(
                                "\"cuisines\":[\"sichuan\",\"cantonese\"]"
                        )
        );

        request.setValue(List.of());

        service.adjustAndRank(1L, request);

        assertTrue(
                sessionState.getCurrentConstraints()
                        .contains("\"cuisines\":[]")
        );
    }

    @Test
    void shouldGenerateDifferentRequestIdForEachAdjustment() {
        stubSessionStateAndMerchants(
                List.of(createMerchant(
                        606L,
                        "repeated adjustment",
                        new BigDecimal("4.8"),
                        20
                )),
                "{\"scenes\":[\"friends\"]}"
        );
        stubCalculatedScores();

        RecommendationAdjustRequest first =
                createAdjustRequest("scenes", List.of());
        RecommendationAdjustRequest second =
                createAdjustRequest(
                        "perCapitaBudget",
                        new BigDecimal("100")
                );

        RecommendationRankResponse firstResponse =
                service.adjustAndRank(1L, first);
        RecommendationRankResponse secondResponse =
                service.adjustAndRank(1L, second);

        ArgumentCaptor<Recommendation> captor =
                ArgumentCaptor.forClass(Recommendation.class);
        verify(recommendationMapper, times(2))
                .insert(captor.capture());

        List<Recommendation> recommendations =
                captor.getAllValues();
        assertAll(
                () -> assertNotEquals(
                        recommendations.get(0).getId(),
                        recommendations.get(1).getId()
                ),
                () -> assertNotEquals(
                        recommendations.get(0).getRequestId(),
                        recommendations.get(1).getRequestId()
                ),
                () -> assertTrue(
                        recommendations.get(0).getRequestId()
                                .startsWith("rank-")
                ),
                () -> assertTrue(
                        recommendations.get(1).getRequestId()
                                .startsWith("rank-")
                ),
                () -> assertEquals(
                        recommendations.get(0).getRequestId(),
                        firstResponse.getRequestId()
                ),
                () -> assertEquals(
                        recommendations.get(1).getRequestId(),
                        secondResponse.getRequestId()
                )
        );
    }

    @Test
    void shouldAbortAdjustmentWhenRecommendationInsertFails() {
        stubSessionStateAndMerchants(
                List.of(createMerchant(
                        607L,
                        "insert failure",
                        new BigDecimal("4.8"),
                        20
                )),
                "{\"scenes\":[\"friends\"]}"
        );
        when(recommendationMapper.insert(
                any(Recommendation.class)
        )).thenThrow(
                new DataRetrievalFailureException(
                        "recommendation insert failed"
                )
        );

        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> service.adjustAndRank(
                                1L,
                                createAdjustRequest(
                                        "scenes",
                                        List.of()
                                )
                        )
                );

        assertAll(
                () -> assertEquals(
                        "RECOMMENDATION_DATA_SERVICE_ERROR",
                        exception.getCode()
                ),
                () -> verify(
                        recommendationItemMapper,
                        never()
                ).insert(any(RecommendationItem.class))
        );
    }

    @Test
    void shouldRejectAdjustmentWhenUserDoesNotOwnSession() {
        stubSessionStateAndMerchants(
                List.of(createMerchant(
                        605L,
                        "other user",
                        new BigDecimal("4.8"),
                        20
                )),
                "{}"
        );

        RecommendationAdjustRequest request =
                createAdjustRequest(
                        "perCapitaBudget",
                        new BigDecimal("80")
                );
        request.setUserId(2L);

        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> service.adjustAndRank(1L, request)
                );

        assertAll(
                () -> assertEquals(
                        "SESSION_ACCESS_DENIED",
                        exception.getCode()
                ),
                () -> verify(
                        chatSessionStateMapper,
                        never()
                ).updateById(any(ChatSessionState.class)),
                () -> verify(
                        recommendationMapper,
                        never()
                ).insert(any(Recommendation.class))
        );
    }

    private void assertInvalidAdjustment(
            String field,
            Object value
    ) {
        RecommendationAdjustRequest request =
                createAdjustRequest(field, value);

        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> service.adjustAndRank(1L, request)
                );

        assertEquals(
                "INVALID_RECOMMENDATION_ADJUSTMENT",
                exception.getCode()
        );
    }

    private RecommendationAdjustRequest createAdjustRequest(
            String field,
            Object value
    ) {
        RecommendationAdjustRequest request =
                new RecommendationAdjustRequest();
        request.setUserId(1L);
        request.setField(field);
        request.setValue(value);
        request.setUserLatitude(
                new BigDecimal("30.5728")
        );
        request.setUserLongitude(
                new BigDecimal("104.0668")
        );
        request.setWeights(createDefaultWeights());
        return request;
    }

    private void stubCalculatedScores() {
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
    }

    private void stubNoCalculatedResult() {
        when(matchScoreCalculator.calculate(
                any(Merchant.class),
                any(ConstraintState.class),
                any(RecommendationWeights.class),
                any(BigDecimal.class),
                any(BigDecimal.class)
        )).thenReturn(Optional.empty());
    }

    private ChatSessionState stubSessionStateAndMerchants(
            List<Merchant> merchants,
            String constraintsJson
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
        sessionState.setConversationStage("SEARCHING");
        sessionState.setPendingConfirmation("[]");
        sessionState.setCurrentConstraints(
                constraintsJson
        );
        sessionState.setVersion(1);

        when(chatSessionMapper.selectById(1L))
                .thenReturn(session);
        lenient().when(chatSessionStateMapper.selectOne(any()))
                .thenReturn(sessionState);
        lenient().when(merchantMapper.selectList(any()))
                .thenReturn(merchants);

        return sessionState;
    }

    private String defaultConstraintsJson() {
        return """
                {
                  "partySize": 4,
                  "perCapitaBudget": 80,
                  "cuisines": ["sichuan"],
                  "excludedMerchantTypes": ["hotpot"],
                  "distanceKm": 3,
                  "minRating": 4.5,
                  "environmentRequirements": ["quiet"]
                }
                """;
    }

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

    private RecommendationWeights createDefaultWeights() {
        RecommendationWeights weights =
                new RecommendationWeights();
        weights.setCuisine(new BigDecimal("25"));
        weights.setRating(new BigDecimal("20"));
        weights.setPrice(new BigDecimal("20"));
        weights.setDistance(new BigDecimal("15"));
        weights.setEnvironment(new BigDecimal("10"));
        weights.setReputation(new BigDecimal("10"));
        return weights;
    }

    private RecommendationWeights
    createDistanceFocusedWeights() {
        RecommendationWeights weights =
                new RecommendationWeights();
        weights.setCuisine(new BigDecimal("10"));
        weights.setRating(new BigDecimal("30"));
        weights.setPrice(new BigDecimal("10"));
        weights.setDistance(new BigDecimal("30"));
        weights.setEnvironment(new BigDecimal("10"));
        weights.setReputation(new BigDecimal("10"));
        return weights;
    }

    private Merchant createMerchant(
            Long id,
            String name,
            BigDecimal rating,
            Integer reviewCount
    ) {
        Merchant merchant =
                new Merchant();
        merchant.setId(id);
        merchant.setMerchantCode("TEST_" + id);
        merchant.setName(name);
        merchant.setCategory("sichuan");
        merchant.setCuisine("sichuan");
        merchant.setRating(rating);
        merchant.setAveragePrice(
                new BigDecimal("68.00")
        );
        merchant.setReviewCount(reviewCount);
        merchant.setAddress("test address");
        merchant.setLongitude(
                new BigDecimal("104.067000")
        );
        merchant.setLatitude(
                new BigDecimal("30.573000")
        );
        merchant.setEnvironmentTags("[\"quiet\"]");
        merchant.setPlatformStatus("ACTIVE");
        merchant.setOperationStatus("OPERATING");
        return merchant;
    }

    private RecommendationItemVO createCalculatedResult(
            Merchant merchant,
            BigDecimal finalScore
    ) {
        RecommendationItemVO result =
                new RecommendationItemVO();
        result.setMerchantId(merchant.getId());
        result.setMerchantName(merchant.getName());
        result.setCategory(merchant.getCategory());
        result.setCuisine(merchant.getCuisine());
        result.setMerchantRating(merchant.getRating());
        result.setAveragePrice(
                merchant.getAveragePrice()
        );
        result.setReviewCount(
                merchant.getReviewCount()
        );
        result.setDistanceKm(new BigDecimal("1.00"));
        result.setFinalScore(finalScore);
        result.getMatchedConditions()
                .add("test match one");
        result.getMatchedConditions()
                .add("test match two");
        result.setReason(
                merchant.getName()
                        + " score "
                        + finalScore
        );
        return result;
    }
}
