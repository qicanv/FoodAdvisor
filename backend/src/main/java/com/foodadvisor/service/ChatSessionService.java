package com.foodadvisor.service;

import com.foodadvisor.dto.session.ChatSessionCreateRequest;
import com.foodadvisor.dto.session.ChatSessionCreateResponse;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.ChatSessionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class ChatSessionService {

    static final String DEFAULT_TITLE = "AI探店对话";

    private final ChatSessionMapper chatSessionMapper;

    public ChatSessionService(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    @Transactional
    public ChatSessionCreateResponse create(
            Long userId,
            ChatSessionCreateRequest request
    ) {
        if (userId == null || userId <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "缺少有效用户身份"
            );
        }

        String title = request == null
                ? null
                : request.getTitle();
        title = title == null || title.isBlank()
                ? DEFAULT_TITLE
                : title.trim();

        OffsetDateTime now = OffsetDateTime.now();
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        session.setStatus("ACTIVE");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setClosedAt(null);

        int rows = chatSessionMapper.insert(session);
        if (rows != 1 || session.getId() == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SESSION_CREATE_FAILED",
                    "对话会话创建失败"
            );
        }

        ChatSessionCreateResponse response =
                new ChatSessionCreateResponse();
        response.setSessionId(session.getId());
        response.setTitle(session.getTitle());
        response.setStatus(session.getStatus());
        response.setCreatedAt(session.getCreatedAt());
        return response;
    }
}
