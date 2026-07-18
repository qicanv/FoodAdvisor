package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.session.ChatSessionCreateRequest;
import com.foodadvisor.dto.session.ChatSessionCreateResponse;
import com.foodadvisor.service.ChatSessionService;
import com.foodadvisor.util.AuthenticatedUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diner/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(
            ChatSessionService chatSessionService
    ) {
        this.chatSessionService = chatSessionService;
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
}
