package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.dialogue.DialogueContinueRequest;
import com.foodadvisor.dto.dialogue.DialogueContinueResponse;
import com.foodadvisor.dto.dialogue.DialogueHistoryResponse;
import com.foodadvisor.dto.dialogue.DialogueMessageRequest;
import com.foodadvisor.dto.dialogue.DialogueMessageResponse;
import com.foodadvisor.service.DiningDialogueMessageService;
import com.foodadvisor.service.DialogueService;
import com.foodadvisor.util.AuthenticatedUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多轮对话与需求追问接口。
 */
@RestController
@RequestMapping("/api/diner/sessions")
public class DialogueController {

    private final DialogueService dialogueService;

    private final DiningDialogueMessageService
            diningDialogueMessageService;

    public DialogueController(
            DialogueService dialogueService,
            DiningDialogueMessageService
                    diningDialogueMessageService
    ) {
        this.dialogueService = dialogueService;
        this.diningDialogueMessageService =
                diningDialogueMessageService;
    }

    /**
     * 继续指定会话的一轮对话。
     */
    @PostMapping("/{sessionId}/dialogue/continue")
    public ApiResponse<DialogueContinueResponse>
            continueDialogue(
            @PathVariable Long sessionId,
            @Valid @RequestBody
            DialogueContinueRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Long userId = AuthenticatedUserId.require(httpRequest);
        request.setUserId(userId);
        DialogueContinueResponse response =
                dialogueService.continueDialogue(
                        sessionId,
                        userId,
                        request.getMessage()
                );
        httpResponse.setHeader("X-Trace-Id", response.getTraceId());

        return ApiResponse.success(response);
    }

    @PostMapping("/{sessionId}/messages")
    public ApiResponse<DialogueMessageResponse>
            sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody
            DialogueMessageRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        request.setUserId(
                AuthenticatedUserId.require(httpRequest)
        );
        DialogueMessageResponse response =
                diningDialogueMessageService.sendMessage(
                        sessionId,
                        request
                );
        httpResponse.setHeader("X-Trace-Id", response.getTraceId());

        return ApiResponse.success(response);
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<DialogueHistoryResponse>
            listMessages(
            @PathVariable Long sessionId,
            HttpServletRequest httpRequest
    ) {
        DialogueHistoryResponse response =
                diningDialogueMessageService.listMessages(
                        sessionId,
                        AuthenticatedUserId.require(httpRequest)
                );

        return ApiResponse.success(response);
    }
}
