package com.foodadvisor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.session.ChatSessionCreateResponse;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.service.ChatSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatSessionControllerTest {

    @Mock
    private ChatSessionService chatSessionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ChatSessionController(chatSessionService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateSessionWithJwtUserId() throws Exception {
        ChatSessionCreateResponse response =
                new ChatSessionCreateResponse();
        response.setSessionId(10L);
        response.setTitle("周末聚餐");
        response.setStatus("ACTIVE");
        response.setCreatedAt(
                OffsetDateTime.parse("2026-07-18T10:00:00+08:00")
        );
        when(chatSessionService.create(eq(7L), argThat(
                request -> "周末聚餐".equals(request.getTitle())
        ))).thenReturn(response);

        mockMvc.perform(post("/api/diner/sessions")
                        .requestAttr("userId", 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"周末聚餐\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.sessionId").value(10))
                .andExpect(jsonPath("$.data.title").value("周末聚餐"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").exists());

        verify(chatSessionService).create(eq(7L), argThat(
                request -> "周末聚餐".equals(request.getTitle())
        ));
    }

    @Test
    void shouldAllowEmptyRequestBody() throws Exception {
        ChatSessionCreateResponse response =
                new ChatSessionCreateResponse();
        response.setSessionId(11L);
        response.setTitle("AI探店对话");
        response.setStatus("ACTIVE");
        response.setCreatedAt(OffsetDateTime.now());
        when(chatSessionService.create(7L, null))
                .thenReturn(response);

        mockMvc.perform(post("/api/diner/sessions")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title")
                        .value("AI探店对话"));
    }

    @Test
    void shouldReturnUnauthorizedWithoutJwtUserId()
            throws Exception {
        mockMvc.perform(post("/api/diner/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
