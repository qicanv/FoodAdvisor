package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.prompt.ResolvedPrompt;
import com.foodadvisor.dto.ai.DialogueExtractAiRequest;
import com.foodadvisor.dto.ai.RuntimeModelConfig;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.enums.PromptScene;
import com.foodadvisor.entity.AiCallLog;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.util.SensitiveLogSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;

@ExtendWith(MockitoExtension.class)
class AIClientServiceTest {

    @Mock
    private AiCallLogService aiCallLogService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PromptManagementService promptManagementService;

    @Mock
    private RuntimeModelConfigResolver runtimeModelConfigResolver;

    private AIClientService aiClientService;
    private MockRestServiceServer server;
    private ObjectMapper objectMapper;
    private AtomicLong aiCallLogIds;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        aiCallLogIds = new AtomicLong(700L);
        aiClientService = new AIClientService(
                objectMapper,
                aiCallLogService,
                auditLogService,
                new SensitiveLogSanitizer(),
                null,
                promptManagementService,
                runtimeModelConfigResolver
        );
        ReflectionTestUtils.setField(
                aiClientService,
                "aiServiceBaseUrl",
                "http://ai-service"
        );
        ReflectionTestUtils.setField(
                aiClientService,
                "internalToken",
                "internal-secret-token"
        );

        RestTemplate restTemplate =
                (RestTemplate) ReflectionTestUtils.getField(
                        aiClientService,
                        "restTemplate"
                );
        server = MockRestServiceServer.bindTo(restTemplate).build();

        lenient().doAnswer(invocation -> {
            AiCallLog log = invocation.getArgument(0);
            log.setId(aiCallLogIds.incrementAndGet());
            return null;
        }).when(aiCallLogService).recordSafely(any(AiCallLog.class));

        lenient().when(
                promptManagementService.resolveActivePrompt(
                        any(PromptScene.class)
                )
        ).thenReturn(Optional.empty());
    }

    @Test
    void shouldWriteAiCallLogAndAuditLogOnSuccess()
            throws Exception {
        server.expect(once(), requestTo(
                        "http://ai-service/internal/reviews/analyze"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Trace-Id", org.hamcrest.Matchers.startsWith("trc-")))
                .andExpect(header("X-Request-Id", org.hamcrest.Matchers.startsWith("req-")))
                .andExpect(header("X-AI-Stage", "MODEL_CALL"))
                .andRespond(withSuccess(
                        "{\"sentiment\":\"POSITIVE\",\"confidence\":0.9,\"modelName\":\"demo-model\"}",
                        MediaType.APPLICATION_JSON
                ));

        JsonNode result = aiClientService.analyzeReview(
                11L,
                22L,
                "full review text should not be logged",
                3
        );

        assertEquals("POSITIVE", result.get("sentiment").asText());

        AiCallLog aiLog = capturedAiLog();
        AuditLog auditLog = capturedAuditLog();

        assertAll(
                () -> assertEquals("REVIEW_ANALYSIS",
                        aiLog.getFunctionType()),
                () -> assertEquals("SUCCESS", aiLog.getStatus()),
                () -> assertNotNull(aiLog.getTraceId()),
                () -> assertTrue(aiLog.getTraceId().startsWith("ai-")),
                () -> assertNotNull(aiLog.getRootTraceId()),
                () -> assertTrue(aiLog.getRootTraceId().startsWith("trc-")),
                () -> assertEquals("MODEL_CALL", aiLog.getStageName()),
                () -> assertTrue(aiLog.getLatencyMs() >= 0),
                () -> assertFalse(aiLog.getRequestSummary()
                        .contains("full review text")),
                () -> assertFalse(aiLog.getResponseSummary()
                        .contains("POSITIVE")),
                () -> assertEquals("AI_CALL",
                        auditLog.getOperationType()),
                () -> assertEquals("AI", auditLog.getModule()),
                () -> assertEquals("INFO", auditLog.getLevel()),
                () -> assertEquals("SUCCESS", auditLog.getResult()),
                () -> assertEquals(aiLog.getTraceId(),
                        auditLog.getBusinessTraceId()),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"aiCallLogId\":" + aiLog.getId()))
        );
        server.verify();
    }

    @Test
    void shouldWriteFailureLogsWithLatencyAndErrorLevel()
            throws Exception {
        server.expect(once(), requestTo(
                        "http://ai-service/internal/reviews/analyze"
                ))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError()
                        .body("Authorization: Bearer leaked-token password=secret full response body")
                        .contentType(MediaType.TEXT_PLAIN));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> aiClientService.analyzeReview(
                        11L,
                        22L,
                        "full review text should not be logged",
                        3
                )
        );

        assertEquals("AI 服务请求失败，HTTP 状态码：500，响应："
                + "Authorization: Bearer leaked-token password=secret full response body",
                exception.getMessage());

        AiCallLog aiLog = capturedAiLog();
        AuditLog auditLog = capturedAuditLog();
        String serialized = aiLog + " " + auditLog;

        assertAll(
                () -> assertEquals("FAILED", aiLog.getStatus()),
                () -> assertEquals("AI_SERVICE_UNAVAILABLE",
                        aiLog.getErrorType()),
                () -> assertTrue(aiLog.getLatencyMs() >= 0),
                () -> assertEquals("ERROR", auditLog.getLevel()),
                () -> assertEquals("FAILURE", auditLog.getResult()),
                () -> assertEquals(aiLog.getTraceId(),
                        auditLog.getBusinessTraceId()),
                () -> assertFalse(serialized.contains("leaked-token")),
                () -> assertFalse(serialized.contains("password=secret")),
                () -> assertFalse(serialized.contains("full review text"))
        );
        server.verify();
    }

    @Test
    void shouldNotChangeSuccessWhenLogWriteFails()
            throws Exception {
        doThrow(new RuntimeException("ai log down"))
                .when(aiCallLogService)
                .recordSafely(any(AiCallLog.class));
        doThrow(new RuntimeException("audit log down"))
                .when(auditLogService)
                .recordSafely(any(AuditLog.class));

        server.expect(once(), requestTo(
                        "http://ai-service/internal/reviews/analyze"
                ))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"sentiment\":\"POSITIVE\",\"confidence\":0.9}",
                        MediaType.APPLICATION_JSON
                ));

        JsonNode result = aiClientService.analyzeReview(
                11L,
                22L,
                "content",
                1
        );

        assertEquals("POSITIVE", result.get("sentiment").asText());
        server.verify();
    }

    @Test
    void shouldNotCoverOriginalAiExceptionWhenLogWriteFails()
            throws Exception {
        doThrow(new RuntimeException("ai log down"))
                .when(aiCallLogService)
                .recordSafely(any(AiCallLog.class));
        doThrow(new RuntimeException("audit log down"))
                .when(auditLogService)
                .recordSafely(any(AuditLog.class));

        server.expect(once(), requestTo(
                        "http://ai-service/internal/reviews/analyze"
                ))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError()
                        .body("server unavailable")
                        .contentType(MediaType.TEXT_PLAIN));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> aiClientService.analyzeReview(11L, 22L, "content", 1)
        );

        assertEquals("AI 服务请求失败，HTTP 状态码：500，响应：server unavailable",
                exception.getMessage());
        server.verify();
    }

    @Test
    void shouldWriteOnlyOneLogPairForOneRequest()
            throws Exception {
        server.expect(once(), requestTo(
                        "http://ai-service/internal/reviews/batch-analyze"
                ))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"status\":\"SUCCESS\"}",
                        MediaType.APPLICATION_JSON
                ));

        aiClientService.batchAnalyzeReviews(
                java.util.List.of(
                        Map.of("reviewId", 1, "content", "first"),
                        Map.of("reviewId", 2, "content", "second")
                ),
                null  // analysisMode: null = use server default
        );

        verify(aiCallLogService, times(1))
                .recordSafely(any(AiCallLog.class));
        verify(auditLogService, times(1))
                .recordSafely(any(AuditLog.class));

        AiCallLog aiLog = capturedAiLog();
        assertEquals("BATCH_REVIEW_ANALYSIS", aiLog.getFunctionType());
        assertTrue(aiLog.getRequestSummary().contains("\"batchSize\":2"));
        assertFalse(aiLog.getRequestSummary().contains("first"));
        assertFalse(aiLog.getRequestSummary().contains("second"));
        server.verify();
    }

    @Test
    void shouldAttachActiveRuntimePromptToAiRequest()
            throws Exception {
        ResolvedPrompt resolvedPrompt = new ResolvedPrompt(
                1L,
                11L,
                PromptScene.SENTIMENT_ANALYSIS.getCode(),
                3,
                "sentiment-analysis:v3",
                "runtime sentiment prompt"
        );

        when(
                promptManagementService.resolveActivePrompt(
                        PromptScene.SENTIMENT_ANALYSIS
                )
        ).thenReturn(Optional.of(resolvedPrompt));

        server.expect(once(), requestTo(
                        "http://ai-service/internal/reviews/analyze"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString(
                                "\"systemPrompt\":\"runtime sentiment prompt\""
                        ),
                        containsString(
                                "\"promptVersion\":\"sentiment-analysis:v3\""
                        )
                )))
                .andRespond(withSuccess(
                        """
                        {
                          "sentiment": "POSITIVE",
                          "confidence": 0.9,
                          "modelName": "demo-model"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        JsonNode result = aiClientService.analyzeReview(
                11L,
                22L,
                "good food",
                3
        );

        assertEquals(
                "POSITIVE",
                result.get("sentiment").asText()
        );

        verify(promptManagementService).resolveActivePrompt(
                PromptScene.SENTIMENT_ANALYSIS
        );

        server.verify();
    }

    @Test
    void shouldKeepOriginalRequestWhenNoPromptIsActive()
            throws Exception {
        when(
                promptManagementService.resolveActivePrompt(
                        PromptScene.SENTIMENT_ANALYSIS
                )
        ).thenReturn(Optional.empty());

        server.expect(once(), requestTo(
                        "http://ai-service/internal/reviews/analyze"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        not(containsString("\"systemPrompt\"")),
                        not(containsString("\"promptVersion\""))
                )))
                .andRespond(withSuccess(
                        """
                        {
                          "sentiment": "NEUTRAL",
                          "confidence": 0.6
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        JsonNode result = aiClientService.analyzeReview(
                11L,
                22L,
                "normal review",
                1
        );

        assertEquals(
                "NEUTRAL",
                result.get("sentiment").asText()
        );

        server.verify();
    }

    @Test
    void shouldKeepAiRequestAvailableWhenPromptResolutionFails()
            throws Exception {
        when(
                promptManagementService.resolveActivePrompt(
                        PromptScene.SENTIMENT_ANALYSIS
                )
        ).thenThrow(
                new RuntimeException("prompt database unavailable")
        );

        server.expect(once(), requestTo(
                        "http://ai-service/internal/reviews/analyze"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        not(containsString("\"systemPrompt\"")),
                        not(containsString("\"promptVersion\""))
                )))
                .andRespond(withSuccess(
                        """
                        {
                          "sentiment": "POSITIVE",
                          "confidence": 0.8
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        JsonNode result = aiClientService.analyzeReview(
                11L,
                22L,
                "good service",
                1
        );

        assertEquals(
                "POSITIVE",
                result.get("sentiment").asText()
        );

        server.verify();
    }

    @Test
    void shouldAttachRuntimeModelToDialogueRequestWithoutLoggingSecret()
            throws Exception {
        when(
                runtimeModelConfigResolver
                        .resolveStoreRecommendation()
        ).thenReturn(
                new RuntimeModelConfig(
                        "OPENAI_COMPATIBLE",
                        "runtime-model",
                        "https://model.example.com/v1",
                        "plain-runtime-secret",
                        30000,
                        new BigDecimal("0.25"),
                        1500
                )
        );

        server.expect(once(), requestTo(
                        "http://ai-service/internal/dialogue/extract"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"runtimeModel\""),
                        containsString(
                                "\"provider\":\"OPENAI_COMPATIBLE\""
                        ),
                        containsString(
                                "\"modelName\":\"runtime-model\""
                        ),
                        containsString(
                                "\"baseUrl\":\"https://model.example.com/v1\""
                        ),
                        containsString(
                                "\"apiKey\":\"plain-runtime-secret\""
                        ),
                        containsString("\"timeoutMs\":30000"),
                        containsString("\"temperature\":0.25"),
                        containsString("\"maxOutputTokens\":1500")
                )))
                .andRespond(withSuccess(
                        """
                        {
                          "intent": "MERCHANT_RECOMMENDATION",
                          "extractedConstraints": {},
                          "clearedFields": [],
                          "confidence": 0.9,
                          "extractor": "AI_MODEL",
                          "degraded": false,
                          "modelName": "runtime-model",
                          "provider": "OPENAI_COMPATIBLE",
                          "promptVersion": "constraint-custom:v4"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        DialogueExtractAiRequest request =
                new DialogueExtractAiRequest();
        request.setSessionId(1L);
        request.setMessageId(2L);
        request.setContent("想吃川菜");
        request.setCurrentConstraints(
                new ConstraintState()
        );

        aiClientService.extractDialogueConstraints(
                request
        );

        AiCallLog aiLog = capturedAiLog();
        AuditLog auditLog = capturedAuditLog();

        assertAll(
                () -> assertFalse(
                        aiLog.getRequestSummary()
                                .contains("plain-runtime-secret")
                ),
                () -> assertNull(
                        aiLog.getErrorMessage()
                ),
                () -> assertFalse(
                        auditLog.getMetadata()
                                .contains("plain-runtime-secret")
                )
        );

        verify(runtimeModelConfigResolver)
                .resolveStoreRecommendation();

        server.verify();
    }

    private AiCallLog capturedAiLog() {
        ArgumentCaptor<AiCallLog> captor =
                ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogService).recordSafely(captor.capture());
        return captor.getValue();
    }

    private AuditLog capturedAuditLog() {
        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).recordSafely(captor.capture());
        return captor.getValue();
    }
}
