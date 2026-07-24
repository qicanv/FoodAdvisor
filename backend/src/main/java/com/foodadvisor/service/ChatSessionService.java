package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.foodadvisor.dto.session.ChatSessionCreateRequest;
import com.foodadvisor.dto.session.ChatSessionCreateResponse;
import com.foodadvisor.dto.session.ChatSessionSummaryResponse;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.ChatSessionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

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
        validateUserId(userId);

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

    @Transactional(readOnly = true)
    public List<ChatSessionSummaryResponse> listUserSessions(
            Long userId
    ) {
        validateUserId(userId);

        List<ChatSession> sessions =
                chatSessionMapper.selectList(
                        new LambdaQueryWrapper<ChatSession>()
                                .eq(
                                        ChatSession::getUserId,
                                        userId
                                )
                                .ne(
                                        ChatSession::getStatus,
                                        "ARCHIVED"
                                )
                                .orderByDesc(
                                        ChatSession::getUpdatedAt
                                )
                                .orderByDesc(
                                        ChatSession::getId
                                )
                );

        return sessions.stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional
    public void archiveUserSession(
            Long userId,
            Long sessionId
    ) {
        validateUserId(userId);

        if (sessionId == null || sessionId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SESSION_REQUIRED",
                    "sessionId不能为空"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        int rows = chatSessionMapper.update(
                null,
                new LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .ne(ChatSession::getStatus, "ARCHIVED")
                        .set(ChatSession::getStatus, "ARCHIVED")
                        .set(ChatSession::getClosedAt, now)
                        .set(ChatSession::getUpdatedAt, now)
        );

        if (rows != 1) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SESSION_NOT_FOUND",
                    "对话不存在或已被删除"
            );
        }
    }

    private ChatSessionSummaryResponse toSummaryResponse(
            ChatSession session
    ) {
        ChatSessionSummaryResponse response =
                new ChatSessionSummaryResponse();

        response.setSessionId(session.getId());
        response.setTitle(session.getTitle());
        response.setStatus(session.getStatus());
        response.setCreatedAt(session.getCreatedAt());
        response.setUpdatedAt(session.getUpdatedAt());

        return response;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "缺少有效用户身份"
            );
        }
    }
}