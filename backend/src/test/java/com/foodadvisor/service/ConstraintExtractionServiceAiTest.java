package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.ai.DialogueExtractAiRequest;
import com.foodadvisor.dto.ai.DialogueExtractAiResponse;
import com.foodadvisor.dto.constraint.ConstraintExtractResponse;
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
        return response;
    }
}
