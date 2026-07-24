package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.session.ChatSessionCreateRequest;
import com.foodadvisor.dto.session.ChatSessionCreateResponse;
import com.foodadvisor.dto.session.ChatSessionSummaryResponse;
import com.foodadvisor.service.ChatSessionService;
import com.foodadvisor.util.AuthenticatedUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/diner/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(
            ChatSessionService chatSessionService
    ) {
        this.chatSessionService = chatSessionService;
    }

    @GetMapping
    public ApiResponse<List<ChatSessionSummaryResponse>> list(
            HttpServletRequest httpRequest
    ) {
        Long userId =
                AuthenticatedUserId.require(httpRequest);

        return ApiResponse.success(
                chatSessionService.listUserSessions(userId)
        );
    }

    @PostMapping
    public ApiResponse<ChatSessionCreateResponse> create(
            @Valid @RequestBody(required = false)
            ChatSessionCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                chatSessionService.create(
                        AuthenticatedUserId.require(httpRequest),
                        request
                )
        );
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> archive(
            @PathVariable Long sessionId,
            HttpServletRequest httpRequest
    ) {
        chatSessionService.archiveUserSession(
                AuthenticatedUserId.require(httpRequest),
                sessionId
        );

        return ApiResponse.success(null);
    }
}