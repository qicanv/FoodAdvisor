package com.foodadvisor.service;

import com.foodadvisor.dto.session.ChatSessionCreateRequest;
import com.foodadvisor.dto.session.ChatSessionCreateResponse;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.mapper.ChatSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Test
    void shouldPersistJwtUserAndReturnCreatedSession() {
        when(chatSessionMapper.insert(
                org.mockito.ArgumentMatchers.any(
                        ChatSession.class
                )
        )).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(15L);
            return 1;
        });

        ChatSessionCreateRequest request =
                new ChatSessionCreateRequest();
        request.setTitle("  周末聚餐  ");
        ChatSessionCreateResponse response =
                new ChatSessionService(chatSessionMapper)
                        .create(7L, request);

        ArgumentCaptor<ChatSession> captor =
                ArgumentCaptor.forClass(ChatSession.class);
        org.mockito.Mockito.verify(chatSessionMapper)
                .insert(captor.capture());
        ChatSession saved = captor.getValue();

        assertEquals(7L, saved.getUserId());
        assertEquals("周末聚餐", saved.getTitle());
        assertEquals("ACTIVE", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertNull(saved.getClosedAt());
        assertEquals(15L, response.getSessionId());
        assertEquals("ACTIVE", response.getStatus());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    void shouldUseDefaultTitleForBlankTitle() {
        when(chatSessionMapper.insert(
                org.mockito.ArgumentMatchers.any(
                        ChatSession.class
                )
        )).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(16L);
            return 1;
        });

        ChatSessionCreateRequest request =
                new ChatSessionCreateRequest();
        request.setTitle("   ");
        ChatSessionCreateResponse response =
                new ChatSessionService(chatSessionMapper)
                        .create(7L, request);

        assertEquals("AI探店对话", response.getTitle());
    }
}
