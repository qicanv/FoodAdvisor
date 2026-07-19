package com.foodadvisor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.dto.dialogue.DialogueHistoryResponse;
import com.foodadvisor.dto.dialogue.DialogueContinueResponse;
import com.foodadvisor.dto.dialogue.DialogueMessageResponse;
import com.foodadvisor.dto.dialogue.DialogueMessageVO;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import com.foodadvisor.service.DialogueService;
import com.foodadvisor.service.DiningDialogueMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DialogueControllerTest {

    @Mock
    private DialogueService dialogueService;

    @Mock
    private DiningDialogueMessageService diningDialogueMessageService;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new DialogueController(
                                dialogueService,
                                diningDialogueMessageService
                        )
                )
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .defaultRequest(
                        get("/").requestAttr("userId", 1L)
                )
                .build();
    }

    @Test
    void shouldSendMessageAndReturnRecommendation()
            throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenReturn(messageResponse("RECOMMENDATION"));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("req-1", "想吃川菜")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.responseType")
                        .value("RECOMMENDATION"))
                .andExpect(jsonPath("$.data.recommendation.status")
                        .value("SUCCESS"));
    }

    @Test
    void shouldSendMessageAndReturnClarification()
            throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenReturn(messageResponse("CLARIFICATION"));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("req-2", "想吃饭")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responseType")
                        .value("CLARIFICATION"));
    }

    @Test
    void shouldSendMessageAndReturnNoMatch()
            throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenReturn(messageResponse("NO_MATCH"));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("req-3", "三公里内安静川菜")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responseType")
                        .value("NO_MATCH"))
                .andExpect(jsonPath("$.data.recommendation.status")
                        .value("NO_MATCH"));
    }

    @Test
    void shouldRejectNullMessage() throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST,
                "EMPTY_MESSAGE",
                "消息内容不能为空"
        ));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("req-4-null", null)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("EMPTY_MESSAGE"))
                .andExpect(jsonPath("$.message")
                        .value("消息内容不能为空"));
    }

    @Test
    void shouldRejectEmptyMessage() throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST,
                "EMPTY_MESSAGE",
                "消息内容不能为空"
        ));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("req-4-empty", "")
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("EMPTY_MESSAGE"))
                .andExpect(jsonPath("$.message")
                        .value("消息内容不能为空"));
    }

    @Test
    void shouldRejectBlankMessage() throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST,
                "EMPTY_MESSAGE",
                "消息内容不能为空"
        ));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("req-4", " ")
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("EMPTY_MESSAGE"))
                .andExpect(jsonPath("$.message")
                        .value("消息内容不能为空"));
    }

    @Test
    void shouldReturnUnauthorized() throws Exception {
        MockMvc unauthenticatedMockMvc = MockMvcBuilders
                .standaloneSetup(
                        new DialogueController(
                                dialogueService,
                                diningDialogueMessageService
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        unauthenticatedMockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("req-5", "想吃川菜")
                        )))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED"));
    }

    @Test
    void shouldUseJwtUserIdWhenBodyOmitsUserId()
            throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenReturn(messageResponse("CLARIFICATION"));

        Map<String, Object> body = request("req-jwt", "想吃川菜");
        body.remove("userId");

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .requestAttr("userId", 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(diningDialogueMessageService).sendMessage(
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(
                        request -> Long.valueOf(7L)
                                .equals(request.getUserId())
                )
        );
    }

    @Test
    void shouldIgnoreForgedBodyUserId() throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenReturn(messageResponse("CLARIFICATION"));

        Map<String, Object> body = request("req-forged", "想吃川菜");
        body.put("userId", 999);

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(diningDialogueMessageService).sendMessage(
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(
                        request -> Long.valueOf(7L)
                                .equals(request.getUserId())
                )
        );
    }

    @Test
    void shouldUseJwtUserIdForContinueDialogue()
            throws Exception {
        DialogueContinueResponse response =
                new DialogueContinueResponse();
        response.setSessionId(1L);
        when(dialogueService.continueDialogue(
                1L,
                7L,
                "继续推荐"
        )).thenReturn(response);

        mockMvc.perform(post(
                        "/api/diner/sessions/1/dialogue/continue"
                )
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"userId\":999,"
                                        + "\"message\":\"继续推荐\"}"
                        ))
                .andExpect(status().isOk());

        verify(dialogueService).continueDialogue(
                1L,
                7L,
                "继续推荐"
        );
    }

    @Test
    void shouldReturnForbidden() throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenThrow(new ApiException(
                HttpStatus.FORBIDDEN,
                "SESSION_ACCESS_DENIED",
                "无权访问该对话会话"
        ));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("req-6", "想吃川菜")
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("SESSION_ACCESS_DENIED"));
    }

    @Test
    void shouldReturnRequestProcessing() throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenThrow(new ApiException(
                HttpStatus.CONFLICT,
                "REQUEST_PROCESSING",
                "请求正在处理中，请稍后重试"
        ));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("req-7", "想吃川菜")
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REQUEST_PROCESSING"));
    }

    @Test
    void shouldReturnDataServiceError() throws Exception {
        when(diningDialogueMessageService.sendMessage(
                eq(1L),
                any()
        )).thenThrow(new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RECOMMENDATION_DATA_SERVICE_ERROR",
                "推荐数据服务暂时不可用，请稍后重试"
        ));

        mockMvc.perform(post(
                        "/api/diner/sessions/1/messages"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("req-8", "想吃川菜")
                        )))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("RECOMMENDATION_DATA_SERVICE_ERROR"));
    }

    @Test
    void shouldListMessages() throws Exception {
        DialogueHistoryResponse history =
                new DialogueHistoryResponse();
        history.setSessionId(1L);
        DialogueMessageVO message =
                new DialogueMessageVO();
        message.setId(10L);
        message.setRole("ASSISTANT");
        message.setRequestId("req-9");
        message.setResponseType("RECOMMENDATION");
        message.setRecommendationId(100L);
        history.setMessages(List.of(message));

        when(diningDialogueMessageService.listMessages(
                1L,
                1L
        )).thenReturn(history);

        mockMvc.perform(get(
                        "/api/diner/sessions/1/messages"
                )
                        .param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].id")
                        .value(10))
                .andExpect(jsonPath(
                        "$.data.messages[0].recommendationId"
                ).value(100));

        verify(diningDialogueMessageService)
                .listMessages(1L, 1L);
    }

    @Test
    void shouldReturnUnauthorizedForHistoryWithoutJwtUserId()
            throws Exception {
        MockMvc unauthenticatedMockMvc = MockMvcBuilders
                .standaloneSetup(
                        new DialogueController(
                                dialogueService,
                                diningDialogueMessageService
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        unauthenticatedMockMvc.perform(get(
                        "/api/diner/sessions/1/messages"
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHORIZED"));
    }

    private Map<String, Object> request(
            String requestId,
            String content
    ) {
        Map<String, Object> request =
                new java.util.LinkedHashMap<>();
        request.put("userId", 1);
        request.put("content", content);
        request.put("requestId", requestId);
        request.put("userLatitude", 30.5728);
        request.put("userLongitude", 104.0668);
        return request;
    }

    private DialogueMessageResponse messageResponse(
            String responseType
    ) {
        DialogueMessageResponse response =
                new DialogueMessageResponse();
        response.setSessionId(1L);
        response.setUserMessageId(1L);
        response.setAssistantMessageId(2L);
        response.setRequestId("req");
        response.setResponseType(responseType);
        response.setAssistantText("ok");

        if (!"CLARIFICATION".equals(responseType)) {
            RecommendationRankResponse recommendation =
                    new RecommendationRankResponse();
            recommendation.setStatus(responseType.equals("NO_MATCH")
                    ? "NO_MATCH"
                    : "SUCCESS");
            recommendation.setMatched(
                    !"NO_MATCH".equals(responseType)
            );
            recommendation.setResults(List.of());
            response.setRecommendation(recommendation);
        }

        return response;
    }
}
