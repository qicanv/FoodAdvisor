package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.ai.DialogueExtractAiRequest;
import com.foodadvisor.dto.ai.DialogueExtractAiResponse;
import com.foodadvisor.dto.constraint.ConstraintExtractResponse;
import com.foodadvisor.dto.constraint.ConstraintConflictVO;
import com.foodadvisor.dto.constraint.ConstraintPatch;
import com.foodadvisor.dto.constraint.ConstraintPatchOperations;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.entity.ChatMessage;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.entity.ChatSessionState;
import com.foodadvisor.entity.ConstraintExtraction;
import com.foodadvisor.mapper.ChatMessageMapper;
import com.foodadvisor.mapper.ChatSessionMapper;
import com.foodadvisor.mapper.ChatSessionStateMapper;
import com.foodadvisor.mapper.ConstraintExtractionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ConstraintExtractionServiceAiTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatSessionStateMapper chatSessionStateMapper;

    @Mock
    private ConstraintExtractionMapper constraintExtractionMapper;

    @Mock
    private AIClientService aiClientService;

    private ConstraintExtractionService service;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ConstraintExtractionService(
                chatSessionMapper,
                chatMessageMapper,
                chatSessionStateMapper,
                constraintExtractionMapper,
                aiClientService,
                objectMapper
        );
    }

    @Test
    void shouldUseAiExtractionWhenAiSucceeds()
            throws Exception {
        stubSessionAndPersistence(null);

        ConstraintState extracted = new ConstraintState();
        extracted.setPartySize(4);
        extracted.setPerCapitaBudget(new BigDecimal("80"));
        extracted.setCuisines(List.of("川菜"));

        when(aiClientService.extractDialogueConstraints(any()))
                .thenReturn(aiResponse(
                        "MERCHANT_RECOMMENDATION",
                        extracted,
                        List.of(),
                        false
                ));

        ConstraintExtractResponse response =
                service.extractAndMerge(
                        1L,
                        1L,
                        "四个人人均八十想吃川菜",
                        "req-ai-1"
                );

        assertAll(
                () -> assertEquals(
                        "AI_MODEL",
                        response.getExtractor()
                ),
                () -> assertFalse(response.getDegraded()),
                () -> assertEquals(
                        4,
                        response.getMerged().getPartySize()
                ),
                () -> assertEquals(
                        List.of("川菜"),
                        response.getMerged().getCuisines()
                )
        );

        verify(aiClientService).extractDialogueConstraints(
                any(DialogueExtractAiRequest.class)
        );
        ArgumentCaptor<ConstraintExtraction> extractionCaptor =
                ArgumentCaptor.forClass(ConstraintExtraction.class);
        verify(constraintExtractionMapper).insert(
                extractionCaptor.capture()
        );
        assertEquals(
                "mock-model",
                extractionCaptor.getValue().getModelName()
        );
    }

    @Test
    void shouldFallbackToRulesWhenAiFails() {
        stubSessionAndPersistence(null);
        when(aiClientService.extractDialogueConstraints(any()))
                .thenThrow(new RuntimeException("timeout"));

        ConstraintExtractResponse response =
                service.extractAndMerge(
                        1L,
                        1L,
                        "hello",
                        "req-ai-2"
                );

        assertAll(
                () -> assertEquals(
                        "RULE_FALLBACK",
                        response.getExtractor()
                ),
                () -> assertEquals(
                        true,
                        response.getDegraded()
                )
        );
    }

    @Test
    void shouldExtractBusinessTimeRulesWhenAiFails() {
        stubSessionAndPersistence(null);
        when(aiClientService.extractDialogueConstraints(any()))
                .thenThrow(new RuntimeException("timeout"));

        ConstraintExtractResponse response =
                service.extractAndMerge(
                        1L,
                        1L,
                        "晚上十点后还营业",
                        "req-business-time"
                );

        assertAll(
                () -> assertEquals(
                        "22:00",
                        response.getMerged().getBusinessTargetTime()
                ),
                () -> assertEquals(
                        false,
                        response.getMerged().getBusinessTargetNextDay()
                ),
                () -> assertEquals(
                        null,
                        response.getMerged().getBusinessTime()
                )
        );
    }

    @Test
    void shouldExtractSupportedBusinessTimeExpressions() {
        stubSessionAndPersistence(null);
        when(aiClientService.extractDialogueConstraints(any()))
                .thenThrow(new RuntimeException("timeout"));

        assertEquals(
                "NOW_OPEN",
                extractRules("现在还开门", "req-now")
                        .getBusinessTime()
        );
        assertEquals(
                "TONIGHT",
                extractRules("今晚营业", "req-tonight")
                        .getBusinessTime()
        );
        assertEquals(
                "LATE_NIGHT",
                extractRules(
                        "想吃夜宵，深夜还开",
                        "req-late-night"
                ).getBusinessTime()
        );
        assertEquals(
                "22:30",
                extractRules("22:30还开门", "req-2230")
                        .getBusinessTargetTime()
        );

        ConstraintState afterMidnight =
                extractRules("凌晨1点还营业", "req-0100");
        assertAll(
                () -> assertEquals(
                        "01:00",
                        afterMidnight.getBusinessTargetTime()
                ),
                () -> assertEquals(
                        true,
                        afterMidnight.getBusinessTargetNextDay()
                )
        );

        ConstraintState budget =
                extractRules("人均80元", "req-budget-only");
        assertAll(
                () -> assertEquals(
                        null,
                        budget.getBusinessTargetTime()
                ),
                () -> assertEquals(
                        null,
                        budget.getBusinessTime()
                )
        );
    }

    private ConstraintState extractRules(
            String message,
            String requestId
    ) {
        return service.extractAndMerge(
                1L,
                1L,
                message,
                requestId
        ).getMerged();
    }

    @Test
    void shouldExtractDishKeywordsByRulesWithoutFalsePositives() {
        stubSessionAndPersistence(null);

        assertAll(
                () -> assertEquals(
                        List.of("水煮鱼"),
                        extractRules(
                                "想吃水煮鱼",
                                "req-dish-fish"
                        ).getDishKeywords()
                ),
                () -> assertEquals(
                        List.of("牛肉"),
                        extractRules(
                                "想吃牛肉，人均50",
                                "req-dish-beef"
                        ).getDishKeywords()
                ),
                () -> assertEquals(
                        List.of("小龙虾"),
                        extractRules(
                                "来点小龙虾",
                                "req-dish-crayfish"
                        ).getDishKeywords()
                ),
                () -> assertEquals(
                        List.of("水煮鱼", "烤鱼"),
                        extractRules(
                                "水煮鱼或者烤鱼",
                                "req-dish-or"
                        ).getDishKeywords()
                ),
                () -> assertEquals(
                        List.of(),
                        extractRules(
                                "不想吃烤鱼",
                                "req-dish-negated"
                        ).getDishKeywords()
                ),
                () -> assertEquals(
                        List.of(),
                        extractRules(
                                "不吃香菜",
                                "req-dish-coriander"
                        ).getDishKeywords()
                ),
                () -> assertEquals(
                        List.of(),
                        extractRules(
                                "人均80元",
                                "req-dish-budget"
                        ).getDishKeywords()
                )
        );
    }

    @Test
    void shouldIgnoreInvalidAiBudgetAndDistance()
            throws Exception {
        stubSessionAndPersistence(null);

        ConstraintState extracted = new ConstraintState();
        extracted.setPerCapitaBudget(new BigDecimal("-1"));
        extracted.setDistanceKm(new BigDecimal("120"));
        extracted.setMinRating(new BigDecimal("6"));

        when(aiClientService.extractDialogueConstraints(any()))
                .thenReturn(aiResponse(
                        "MERCHANT_RECOMMENDATION",
                        extracted,
                        List.of(),
                        false
                ));

        ConstraintExtractResponse response =
                service.extractAndMerge(
                        1L,
                        1L,
                        "bad ai values",
                        "req-ai-3"
                );

        assertAll(
                () -> assertEquals(
                        null,
                        response.getMerged().getPerCapitaBudget()
                ),
                () -> assertEquals(
                        null,
                        response.getMerged().getDistanceKm()
                ),
                () -> assertEquals(
                        null,
                        response.getMerged().getMinRating()
                )
        );
    }

    @Test
    void shouldClearOnlyExplicitAllowedFields()
            throws Exception {
        ChatSessionState oldState = new ChatSessionState();
        oldState.setId(10L);
        oldState.setSessionId(1L);
        oldState.setVersion(1);
        oldState.setCurrentConstraints(
                "{\"cuisines\":[\"川菜\"],"
                        + "\"environmentRequirements\":[\"安静\"],"
                        + "\"merchantTypes\":[],"
                        + "\"tastePreferences\":[],"
                        + "\"tasteRestrictions\":[],"
                        + "\"excludedCuisines\":[],"
                        + "\"excludedMerchantTypes\":[],"
                        + "\"scenes\":[]}"
        );

        stubSessionAndPersistence(oldState);

        when(aiClientService.extractDialogueConstraints(any()))
                .thenReturn(aiResponse(
                        "CONSTRAINT_UPDATE",
                        new ConstraintState(),
                        List.of(
                                "environmentRequirements",
                                "userId"
                        ),
                        false
                ));

        service.extractAndMerge(
                1L,
                1L,
                "环境不用安静",
                "req-ai-4"
        );

        ArgumentCaptor<ChatSessionState> captor =
                ArgumentCaptor.forClass(ChatSessionState.class);
        verify(chatSessionStateMapper).updateById(
                captor.capture()
        );

        JsonNode json =
                objectMapper.readTree(
                        captor.getValue()
                                .getCurrentConstraints()
                );

        assertAll(
                () -> assertEquals(
                        0,
                        json.get("environmentRequirements")
                                .size()
                ),
                () -> assertEquals(
                        "川菜",
                        json.get("cuisines").get(0).asText()
                )
        );
    }

    @Test
    void shouldSupplementDistanceWhenAiSuccessOmitsIt()
            throws Exception {
        stubSessionAndPersistence(null);
        ConstraintState aiExtracted = new ConstraintState();
        aiExtracted.setCuisines(List.of("川菜"));
        DialogueExtractAiResponse aiResponse = aiResponse(
                "MERCHANT_RECOMMENDATION",
                aiExtracted,
                List.of(),
                false
        );
        ConstraintPatch patch = new ConstraintPatch();
        ConstraintPatchOperations operations =
                new ConstraintPatchOperations();
        operations.setAdd(java.util.Map.of(
                "cuisines", List.of("川菜")));
        patch.setOperations(operations);
        aiResponse.setPatch(patch);
        when(aiClientService.extractDialogueConstraints(any()))
                .thenReturn(aiResponse);

        ConstraintExtractResponse response = service.extractAndMerge(
                1L,
                1L,
                "两个人，人均100元，距离5公里以内，想吃川菜。",
                "req-distance-supplement"
        );

        ArgumentCaptor<ConstraintExtraction> captor =
                ArgumentCaptor.forClass(ConstraintExtraction.class);
        verify(constraintExtractionMapper).insert(captor.capture());
        JsonNode extractedJson = objectMapper.readTree(
                captor.getValue().getExtractedConstraints());
        JsonNode mergedJson = objectMapper.readTree(
                captor.getValue().getMergedConstraints());
        JsonNode changedJson = objectMapper.readTree(
                captor.getValue().getChangedFields());

        assertAll(
                () -> assertEquals(
                        0, new BigDecimal("5").compareTo(
                                response.getExtracted().getDistanceKm())),
                () -> assertEquals(
                        0, new BigDecimal("5").compareTo(
                                response.getMerged().getDistanceKm())),
                () -> assertTrue(response.getChanges()
                        .contains("distanceKm")),
                () -> assertEquals(
                        0, new BigDecimal("5").compareTo(
                                extractedJson.get("distanceKm")
                                        .decimalValue())),
                () -> assertEquals(
                        0, new BigDecimal("5").compareTo(
                                mergedJson.get("distanceKm")
                                        .decimalValue())),
                () -> assertTrue(changedJson.toString()
                        .contains("distanceKm"))
        );
    }

    @Test
    void shouldKeepAiClearAndConflictFromRuleSupplement() {
        ChatSessionState oldState = new ChatSessionState();
        oldState.setId(10L);
        oldState.setSessionId(1L);
        oldState.setVersion(1);
        oldState.setCurrentConstraints(
                "{\"distanceKm\":3,\"merchantTypes\":[],"
                        + "\"cuisines\":[],\"tastePreferences\":[],"
                        + "\"tasteRestrictions\":[],\"dishKeywords\":[],"
                        + "\"excludedCuisines\":[],"
                        + "\"excludedMerchantTypes\":[],\"scenes\":[],"
                        + "\"environmentRequirements\":[]}");
        stubSessionAndPersistence(oldState);

        DialogueExtractAiResponse clearResponse = aiResponse(
                "CONSTRAINT_UPDATE", new ConstraintState(),
                List.of("distanceKm"), false);
        ConstraintPatch clearPatch = new ConstraintPatch();
        ConstraintPatchOperations clearOperations =
                new ConstraintPatchOperations();
        clearOperations.setClear(List.of("distanceKm"));
        clearPatch.setOperations(clearOperations);
        clearResponse.setPatch(clearPatch);

        DialogueExtractAiResponse conflictResponse = aiResponse(
                "CONSTRAINT_UPDATE", new ConstraintState(),
                List.of(), false);
        ConstraintPatch conflictPatch = new ConstraintPatch();
        conflictPatch.setConflicts(List.of(
                new ConstraintConflictVO(
                        "distanceKm", "距离条件冲突")));
        conflictResponse.setPatch(conflictPatch);

        when(aiClientService.extractDialogueConstraints(any()))
                .thenReturn(clearResponse, conflictResponse);

        ConstraintExtractResponse cleared = service.extractAndMerge(
                1L, 1L, "距离仍然5公里以内", "req-clear-distance");
        ConstraintExtractResponse conflicted = service.extractAndMerge(
                1L, 1L, "距离5公里以内", "req-conflict-distance");

        assertAll(
                () -> assertEquals(
                        null, cleared.getMerged().getDistanceKm()),
                () -> assertEquals(
                        null, cleared.getExtracted().getDistanceKm()),
                () -> assertEquals(
                        null, conflicted.getExtracted().getDistanceKm()),
                () -> assertEquals(
                        1, conflicted.getConflicts().size())
        );
    }

    @Test
    void shouldDistinguishAndClearRatingConditions() {
        ChatSessionState oldState = new ChatSessionState();
        oldState.setId(10L);
        oldState.setSessionId(1L);
        oldState.setVersion(1);
        oldState.setCurrentConstraints(
                "{\"minRating\":4.0,\"ratingPreference\":\"HIGH\","
                        + "\"merchantTypes\":[],\"cuisines\":[],"
                        + "\"tastePreferences\":[],"
                        + "\"tasteRestrictions\":[],\"dishKeywords\":[],"
                        + "\"excludedCuisines\":[],"
                        + "\"excludedMerchantTypes\":[],\"scenes\":[],"
                        + "\"environmentRequirements\":[]}");
        stubSessionAndPersistence(oldState);
        when(aiClientService.extractDialogueConstraints(any()))
                .thenReturn(
                        aiResponse("CONSTRAINT_UPDATE",
                                new ConstraintState(), List.of(), false),
                        aiResponse("CONSTRAINT_UPDATE",
                                new ConstraintState(), List.of(), false),
                        aiResponse("CONSTRAINT_UPDATE",
                                new ConstraintState(), List.of(), false)
                );

        ConstraintExtractResponse fuzzy = service.extractAndMerge(
                1L, 1L, "评分较高、适合聚餐", "req-rating-high");
        ConstraintExtractResponse numeric = service.extractAndMerge(
                1L, 1L, "评分4分以上", "req-rating-min");
        ConstraintExtractResponse cleared = service.extractAndMerge(
                1L, 1L, "不用考虑评分", "req-rating-clear");

        assertAll(
                () -> assertEquals(
                        ConstraintState.RATING_PREFERENCE_HIGH,
                        fuzzy.getExtracted().getRatingPreference()),
                () -> assertEquals(
                        null, fuzzy.getExtracted().getMinRating()),
                () -> assertEquals(
                        0, new BigDecimal("4").compareTo(
                                numeric.getExtracted().getMinRating())),
                () -> assertEquals(
                        null, numeric.getExtracted()
                                .getRatingPreference()),
                () -> assertEquals(
                        null, cleared.getMerged().getMinRating()),
                () -> assertEquals(
                        null, cleared.getMerged().getRatingPreference()),
                () -> assertTrue(cleared.getChanges()
                        .containsAll(List.of(
                                "minRating", "ratingPreference")))
        );
    }

    private void stubSessionAndPersistence(
            ChatSessionState existingState
    ) {
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setStatus("ACTIVE");
        when(chatSessionMapper.selectById(1L))
                .thenReturn(session);

        when(chatMessageMapper.insert(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message =
                            invocation.getArgument(0);
                    message.setId(100L);
                    return 1;
                });

        when(chatSessionStateMapper.selectOne(any()))
                .thenReturn(existingState);
        lenient().when(chatSessionStateMapper.insert(
                any(ChatSessionState.class)
        )).thenReturn(1);
        lenient().when(chatSessionStateMapper.updateById(
                any(ChatSessionState.class)
        )).thenReturn(1);

        when(constraintExtractionMapper.insert(
                any(ConstraintExtraction.class)
        )).thenAnswer(invocation -> {
            ConstraintExtraction extraction =
                    invocation.getArgument(0);
            extraction.setId(200L);
            return 1;
        });
    }

    private DialogueExtractAiResponse aiResponse(
            String intent,
            ConstraintState extracted,
            List<String> clearedFields,
            boolean degraded
    ) {
        DialogueExtractAiResponse response =
                new DialogueExtractAiResponse();
        response.setIntent(intent);
        response.setExtractedConstraints(extracted);
        response.setClearedFields(clearedFields);
        response.setConfidence(new BigDecimal("0.9"));
        response.setExtractor("AI_MODEL");
        response.setDegraded(degraded);
        response.setModelName("mock-model");
        return response;
    }
}
