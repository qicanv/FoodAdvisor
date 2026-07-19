package com.foodadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.dialogue.DialogueContinueResponse;
import com.foodadvisor.dto.dialogue.DialogueHistoryResponse;
import com.foodadvisor.dto.dialogue.DialogueMessageRequest;
import com.foodadvisor.dto.dialogue.DialogueMessageResponse;
import com.foodadvisor.dto.recommendation.AdjustmentSuggestionVO;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import com.foodadvisor.entity.ChatMessage;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Recommendation;
import com.foodadvisor.entity.RecommendationItem;
import com.foodadvisor.mapper.ChatMessageMapper;
import com.foodadvisor.mapper.ChatSessionMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.RecommendationItemMapper;
import com.foodadvisor.mapper.RecommendationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiningDialogueMessageServiceTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private RecommendationMapper recommendationMapper;

    @Mock
    private RecommendationItemMapper recommendationItemMapper;

    @Mock
    private MerchantMapper merchantMapper;

    @Mock
    private DialogueService dialogueService;

    @Mock
    private RecommendationRankingService recommendationRankingService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private DiningDialogueMessageService service;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        lenient().when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        service = new DiningDialogueMessageService(
                chatSessionMapper,
                chatMessageMapper,
                recommendationMapper,
                recommendationItemMapper,
                merchantMapper,
                dialogueService,
                recommendationRankingService,
                redisTemplate,
                objectMapper
        );
    }

    @Test
    void shouldRejectBlankMessagesWithEmptyMessageCode() {
        assertEmptyMessage(null);
        assertEmptyMessage("");
        assertEmptyMessage("   ");
    }

    @Test
    void shouldReturnCompleteSnapshotForSameRequestId()
            throws Exception {
        when(chatSessionMapper.selectById(1L))
                .thenReturn(activeSession());

        DialogueMessageResponse snapshot =
                completeResponse();

        ChatMessage userMessage =
                message(10L, "USER", "req-1", "{}");
        ChatMessage assistantMessage =
                message(
                        11L,
                        "ASSISTANT",
                        "req-1",
                        objectMapper.writeValueAsString(
                                Map.of(
                                        "responseSnapshot",
                                        snapshot
                                )
                        )
                );

        when(chatMessageMapper.selectOne(any()))
                .thenReturn(userMessage, assistantMessage);

        DialogueMessageResponse response =
                service.sendMessage(1L, request("req-1"));

        assertAll(
                () -> assertEquals(1L, response.getSessionId()),
                () -> assertEquals(10L, response.getUserMessageId()),
                () -> assertEquals(11L, response.getAssistantMessageId()),
                () -> assertEquals("req-1", response.getRequestId()),
                () -> assertEquals(
                        "NO_MATCH",
                        response.getResponseType()
                ),
                () -> assertEquals(
                        "no match",
                        response.getAssistantText()
                ),
                () -> assertNotNull(response.getRecommendation()),
                () -> assertFalse(
                        response.getRecommendation()
                                .getAdjustmentSuggestions()
                                .isEmpty()
                ),
                () -> assertNotNull(
                        response.getCurrentConstraints()
                ),
                () -> assertEquals(
                        List.of("cuisines"),
                        response.getMissingFields()
                )
        );

        verify(valueOperations, never())
                .setIfAbsent(anyString(), any(), any());
        verify(dialogueService, never())
                .continueDialogue(any(), any(), any(), any());
        verify(recommendationRankingService, never())
                .rank(any(), any());
        verify(chatMessageMapper, never())
                .insert(any(ChatMessage.class));
        verify(recommendationMapper, never())
                .insert(any(Recommendation.class));
    }

    @Test
    void shouldReturnProcessingWhenUserMessageExistsWithoutAssistant() {
        when(chatSessionMapper.selectById(1L))
                .thenReturn(activeSession());

        ChatMessage userMessage =
                message(10L, "USER", "req-2", "{}");

        when(chatMessageMapper.selectOne(any()))
                .thenReturn(
                        userMessage,
                        null,
                        userMessage
                );

        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> service.sendMessage(
                                1L,
                                request("req-2")
                        )
                );

        assertEquals(
                "REQUEST_PROCESSING",
                exception.getCode()
        );
        verify(valueOperations, never())
                .setIfAbsent(anyString(), any(), any());
        verify(dialogueService, never())
                .continueDialogue(any(), any(), any(), any());
        verify(recommendationRankingService, never())
                .rank(any(), any());
    }

    @Test
    void shouldReturnProcessingWhenRedisLockIsHeld() {
        when(chatSessionMapper.selectById(1L))
                .thenReturn(activeSession());
        when(chatMessageMapper.selectOne(any()))
                .thenReturn(null, null, null, null, null);
        when(valueOperations.setIfAbsent(
                anyString(),
                any(),
                any()
        )).thenReturn(false);

        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> service.sendMessage(
                                1L,
                                request("req-3")
                        )
                );

        assertEquals(
                "REQUEST_PROCESSING",
                exception.getCode()
        );
        verify(dialogueService, never())
                .continueDialogue(any(), any(), any(), any());
        verify(recommendationRankingService, never())
                .rank(any(), any());
    }

    @Test
    void shouldReleaseOnlyOwnedLockWhenProcessingFails() {
        when(chatSessionMapper.selectById(1L))
                .thenReturn(activeSession());
        when(chatMessageMapper.selectOne(any()))
                .thenReturn(null, null, null);
        when(valueOperations.setIfAbsent(
                anyString(),
                any(),
                any()
        )).thenReturn(true);
        when(valueOperations.get(anyString()))
                .thenReturn("other-token");
        when(dialogueService.continueDialogue(
                any(),
                any(),
                any(),
                any()
        )).thenThrow(new ApiException(
                HttpStatus.BAD_GATEWAY,
                "MODEL_SERVICE_ERROR",
                "AI服务暂时不可用，请稍后重试"
        ));

        assertThrows(
                ApiException.class,
                () -> service.sendMessage(
                        1L,
                        request("req-4")
                )
        );

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void shouldDeleteOwnedLockAfterProcessing() {
        AtomicReference<Object> token =
                new AtomicReference<>();

        when(chatSessionMapper.selectById(1L))
                .thenReturn(activeSession());
        when(chatMessageMapper.selectOne(any()))
                .thenReturn(null, null, null);
        when(valueOperations.setIfAbsent(
                anyString(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            token.set(invocation.getArgument(1));
            return true;
        });
        when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> token.get());
        when(redisTemplate.delete(anyString()))
                .thenReturn(true);
        when(dialogueService.continueDialogue(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(readyDialogue());
        when(recommendationRankingService.rank(any(), any()))
                .thenReturn(successRecommendation());
        when(chatMessageMapper.insert(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message =
                            invocation.getArgument(0);
                    message.setId(12L);
                    return 1;
                });
        when(chatMessageMapper.updateById(any(ChatMessage.class)))
                .thenReturn(1);
        when(recommendationMapper.selectById(100L))
                .thenReturn(recommendation());
        when(recommendationMapper.updateById(
                any(Recommendation.class)
        )).thenReturn(1);

        service.sendMessage(1L, request("req-5"));

        verify(redisTemplate).delete(anyString());
    }

    @Test
    void shouldNotRecommendForGeneralChatIntent() {
        AtomicReference<Object> token =
                new AtomicReference<>();

        when(chatSessionMapper.selectById(1L))
                .thenReturn(activeSession());
        when(chatMessageMapper.selectOne(any()))
                .thenReturn(null, null, null);
        when(valueOperations.setIfAbsent(
                anyString(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            token.set(invocation.getArgument(1));
            return true;
        });
        when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> token.get());
        when(redisTemplate.delete(anyString()))
                .thenReturn(true);
        when(dialogueService.continueDialogue(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(generalChatDialogue());
        when(chatMessageMapper.insert(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message =
                            invocation.getArgument(0);
                    message.setId(13L);
                    return 1;
                });
        when(chatMessageMapper.updateById(any(ChatMessage.class)))
                .thenReturn(1);

        DialogueMessageResponse response =
                service.sendMessage(1L, request("req-general"));

        assertAll(
                () -> assertEquals(
                        "CLARIFICATION",
                        response.getResponseType()
                ),
                () -> assertEquals(
                        "AI_MODEL",
                        response.getExtractor()
                ),
                () -> assertFalse(response.getDegraded()),
                () -> assertEquals(
                        null,
                        response.getRecommendation()
                )
        );

        verify(recommendationRankingService, never())
                .rank(any(), any());
    }

    @Test
    void shouldSaveNoMatchAsRecommendationAndKeepBusinessMetadata()
            throws Exception {
        prepareRecommendationFlow(noMatchRecommendation());

        DialogueMessageResponse response =
                service.sendMessage(1L, request("req-no-match"));

        ArgumentCaptor<ChatMessage> captor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(captor.capture());
        ChatMessage assistant = captor.getValue();
        Map<String, Object> metadata =
                objectMapper.readValue(
                        assistant.getMetadata(),
                        new com.fasterxml.jackson.core.type.TypeReference<
                                Map<String, Object>>() {
                        }
                );

        assertAll(
                () -> assertEquals(
                        "ASSISTANT",
                        assistant.getRole()
                ),
                () -> assertEquals(
                        "RECOMMENDATION",
                        assistant.getMessageType()
                ),
                () -> assertEquals(
                        "当前没有完全匹配的结果",
                        assistant.getContent()
                ),
                () -> assertEquals(
                        "NO_MATCH",
                        metadata.get("responseType")
                ),
                () -> assertEquals(
                        "NO_MATCH",
                        metadata.get("status")
                ),
                () -> assertEquals(
                        101,
                        metadata.get("recommendationId")
                ),
                () -> assertEquals(
                        "relax cuisine",
                        ((Map<?, ?>) ((List<?>) metadata.get(
                                "adjustmentSuggestions"
                        )).get(0)).get("displayText")
                ),
                () -> assertEquals(
                        "NO_MATCH",
                        response.getResponseType()
                ),
                () -> assertEquals(
                        "relax cuisine",
                        response.getRecommendation()
                                .getAdjustmentSuggestions()
                                .get(0)
                                .getDisplayText()
                )
        );
    }

    @Test
    void shouldSaveNormalRecommendationAsRecommendation() {
        prepareRecommendationFlow(successRecommendation());

        service.sendMessage(1L, request("req-recommendation"));

        ArgumentCaptor<ChatMessage> captor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(captor.capture());

        assertEquals(
                "RECOMMENDATION",
                captor.getValue().getMessageType()
        );
    }

    @Test
    void shouldSafelyMapUnknownResponseTypeToText() {
        assertAll(
                () -> assertEquals(
                        "TEXT",
                        DiningDialogueMessageService
                                .mapResponseTypeToMessageType(
                                        "UNEXPECTED"
                                )
                ),
                () -> assertEquals(
                        "TEXT",
                        DiningDialogueMessageService
                                .mapResponseTypeToMessageType(null)
                ),
                () -> assertEquals(
                        "QUESTION",
                        DiningDialogueMessageService
                                .mapResponseTypeToMessageType(
                                        "CLARIFICATION"
                                )
                )
        );
    }

    @Test
    void shouldRestoreNoMatchResponseTypeFromHistoryMetadata() {
        when(chatSessionMapper.selectById(1L))
                .thenReturn(activeSession());

        ChatMessage assistant =
                message(
                        21L,
                        "ASSISTANT",
                        "req-history-no-match",
                        "{\"responseType\":\"NO_MATCH\","
                                + "\"status\":\"NO_MATCH\","
                                + "\"recommendationId\":101}"
                );
        assistant.setMessageType("RECOMMENDATION");
        when(chatMessageMapper.selectList(any()))
                .thenReturn(List.of(assistant));
        when(recommendationItemMapper.selectList(any()))
                .thenReturn(List.of());

        DialogueHistoryResponse history =
                service.listMessages(1L, 1L);

        assertAll(
                () -> assertEquals(
                        "NO_MATCH",
                        history.getMessages().get(0)
                                .getResponseType()
                ),
                () -> assertEquals(
                        "NO_MATCH",
                        history.getMessages().get(0)
                                .getStatus()
                ),
                () -> assertEquals(
                        101L,
                        history.getMessages().get(0)
                                .getRecommendationId()
                )
        );
    }

    @Test
    void shouldReturnHistoryWithRecommendationCardsFromDatabase() {
        when(chatSessionMapper.selectById(1L))
                .thenReturn(activeSession());

        ChatMessage user =
                message(20L, "USER", "req-6", "{}");
        ChatMessage assistant =
                message(
                        21L,
                        "ASSISTANT",
                         "req-6",
                         "{\"recommendationId\":100,"
                                 + "\"responseType\":\"RECOMMENDATION\","
                                 + "\"status\":\"SUCCESS\","
                                 + "\"responseSnapshot\":{"
                                 + "\"recommendation\":{"
                                 + "\"adjustmentSuggestions\":[{"
                                 + "\"id\":\"expand-distance\","
                                 + "\"displayText\":\"放宽距离\""
                                 + "}]}}}"
                 );

        when(chatMessageMapper.selectList(any()))
                .thenReturn(List.of(user, assistant));

        RecommendationItem item =
                new RecommendationItem();
        item.setRecommendationId(100L);
        item.setMerchantId(200L);
        item.setRankNo(1);
        item.setReason("category and operating status");

        when(recommendationItemMapper.selectList(any()))
                .thenReturn(List.of(item));

        Merchant merchant = new Merchant();
        merchant.setId(200L);
        merchant.setName("db merchant");
        merchant.setCategory("sichuan");
        merchant.setOperationStatus("OPERATING");

        when(merchantMapper.selectById(200L))
                .thenReturn(merchant);

        DialogueHistoryResponse history =
                service.listMessages(1L, 1L);

        assertAll(
                () -> assertEquals(
                        2,
                        history.getMessages().size()
                ),
                () -> assertEquals(
                        100L,
                        history.getMessages()
                                .get(1)
                                .getRecommendationId()
                ),
                () -> assertEquals(
                        200L,
                        history.getMessages()
                                .get(1)
                                .getRecommendations()
                                .get(0)
                                .getMerchantId()
                ),
                () -> assertEquals(
                        "category and operating status",
                        history.getMessages()
                                .get(1)
                                .getRecommendations()
                                .get(0)
                                .getReason()
                ),
                 () -> assertEquals(
                         null,
                         history.getMessages()
                                .get(1)
                                .getRecommendations()
                                 .get(0)
                                 .getAveragePrice()
                ),
                () -> assertEquals(
                        "OPERATING",
                        history.getMessages()
                                .get(1)
                                .getRecommendations()
                                .get(0)
                                .getOperationStatus()
                ),
                () -> assertEquals(
                        "放宽距离",
                        history.getMessages()
                                .get(1)
                                .getAdjustmentSuggestions()
                                .get(0)
                                .getDisplayText()
                 )
         );
    }

    private void assertEmptyMessage(String content) {
        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> service.sendMessage(
                                1L,
                                request("req-empty", content)
                        )
                );

        assertAll(
                () -> assertEquals(
                        HttpStatus.BAD_REQUEST,
                        exception.getStatus()
                ),
                () -> assertEquals(
                        "EMPTY_MESSAGE",
                        exception.getCode()
                )
        );
    }

    private DialogueMessageRequest request(String requestId) {
        return request(requestId, "想吃川菜");
    }

    private DialogueMessageRequest request(
            String requestId,
            String content
    ) {
        DialogueMessageRequest request =
                new DialogueMessageRequest();
        request.setUserId(1L);
        request.setContent(content);
        request.setRequestId(requestId);
        request.setUserLatitude(new BigDecimal("30.5728"));
        request.setUserLongitude(new BigDecimal("104.0668"));
        return request;
    }

    private ChatSession activeSession() {
        ChatSession session =
                new ChatSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setStatus("ACTIVE");
        return session;
    }

    private ChatMessage message(
            Long id,
            String role,
            String requestId,
            String metadata
    ) {
        ChatMessage message =
                new ChatMessage();
        message.setId(id);
        message.setSessionId(1L);
        message.setRole(role);
        message.setRequestId(requestId);
        message.setContent(role + " content");
        message.setMetadata(metadata);
        return message;
    }

    private DialogueMessageResponse completeResponse() {
        ConstraintState constraints =
                new ConstraintState();
        constraints.setPerCapitaBudget(
                new BigDecimal("80")
        );

        RecommendationRankResponse recommendation =
                new RecommendationRankResponse();
        recommendation.setRecommendationId(100L);
        recommendation.setSessionId(1L);
        recommendation.setStatus("NO_MATCH");
        recommendation.setMatched(false);
        recommendation.setResults(List.of());
        recommendation.setAdjustmentSuggestions(
                List.of(new AdjustmentSuggestionVO(
                        "relax-cuisine",
                        "RELAX_CUISINE",
                        "cuisines",
                        List.of("sichuan"),
                        List.of(),
                        "relax cuisine",
                        "no match"
                ))
        );

        DialogueMessageResponse response =
                new DialogueMessageResponse();
        response.setSessionId(1L);
        response.setUserMessageId(10L);
        response.setAssistantMessageId(11L);
        response.setRequestId("req-1");
        response.setResponseType("NO_MATCH");
        response.setAssistantText("no match");
        response.setConversationStage("SEARCHING");
        response.setCurrentConstraints(constraints);
        response.setMissingFields(List.of("cuisines"));
        response.setRecommendation(recommendation);
        response.setExtractor("RULE_FALLBACK");
        response.setDegraded(true);
        return response;
    }

    private DialogueContinueResponse readyDialogue() {
        DialogueContinueResponse response =
                new DialogueContinueResponse();
        response.setSessionId(1L);
        response.setUserMessageId(10L);
        response.setStage("SEARCHING");
        response.setConstraints(new ConstraintState());
        response.setReadyForRecommendation(true);
        return response;
    }

    private DialogueContinueResponse generalChatDialogue() {
        DialogueContinueResponse response =
                new DialogueContinueResponse();
        response.setSessionId(1L);
        response.setUserMessageId(10L);
        response.setStage("COLLECTING");
        response.setConstraints(new ConstraintState());
        response.setReadyForRecommendation(false);
        response.setIntent("GENERAL_CHAT");
        response.setExtractor("AI_MODEL");
        response.setDegraded(false);
        return response;
    }

    private RecommendationRankResponse successRecommendation() {
        RecommendationRankResponse response =
                new RecommendationRankResponse();
        response.setRecommendationId(100L);
        response.setSessionId(1L);
        response.setStatus("SUCCESS");
        response.setMatched(true);
        response.setResultCount(1);
        response.setResults(List.of());
        return response;
    }

    private RecommendationRankResponse noMatchRecommendation() {
        RecommendationRankResponse response =
                new RecommendationRankResponse();
        response.setRecommendationId(101L);
        response.setSessionId(1L);
        response.setStatus("NO_MATCH");
        response.setMatched(false);
        response.setMessage("当前没有完全匹配的结果");
        response.setResultCount(0);
        response.setResults(List.of());
        response.setAdjustmentSuggestions(
                List.of(new AdjustmentSuggestionVO(
                        "relax-cuisine",
                        "RELAX_CUISINE",
                        "cuisines",
                        List.of("sichuan"),
                        List.of(),
                        "relax cuisine",
                        "no match"
                ))
        );
        return response;
    }

    private void prepareRecommendationFlow(
            RecommendationRankResponse rankResponse
    ) {
        AtomicReference<Object> token =
                new AtomicReference<>();
        when(chatSessionMapper.selectById(1L))
                .thenReturn(activeSession());
        when(chatMessageMapper.selectOne(any()))
                .thenReturn(null, null, null);
        when(valueOperations.setIfAbsent(
                anyString(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            token.set(invocation.getArgument(1));
            return true;
        });
        when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> token.get());
        when(redisTemplate.delete(anyString()))
                .thenReturn(true);
        when(dialogueService.continueDialogue(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(readyDialogue());
        when(recommendationRankingService.rank(any(), any()))
                .thenReturn(rankResponse);
        when(chatMessageMapper.insert(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message =
                            invocation.getArgument(0);
                    message.setId(
                            "ASSISTANT".equals(message.getRole())
                                    ? 12L
                                    : 10L
                    );
                    return 1;
                });
        when(chatMessageMapper.updateById(any(ChatMessage.class)))
                .thenReturn(1);

        Recommendation recommendation =
                new Recommendation();
        recommendation.setId(rankResponse.getRecommendationId());
        when(recommendationMapper.selectById(
                rankResponse.getRecommendationId()
        )).thenReturn(recommendation);
        when(recommendationMapper.updateById(
                any(Recommendation.class)
        )).thenReturn(1);
    }

    private Recommendation recommendation() {
        Recommendation recommendation =
                new Recommendation();
        recommendation.setId(100L);
        recommendation.setSessionId(1L);
        recommendation.setStatus("SUCCESS");
        recommendation.setResultCount(1);
        return recommendation;
    }
}
