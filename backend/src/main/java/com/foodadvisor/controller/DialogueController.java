package com.foodadvisor.controller;

import com.foodadvisor.backend.common.ApiResponse;
import com.foodadvisor.dto.dialogue.DialogueContinueRequest;
import com.foodadvisor.dto.dialogue.DialogueContinueResponse;
import com.foodadvisor.dto.dialogue.DialogueHistoryResponse;
import com.foodadvisor.dto.dialogue.DialogueMessageRequest;
import com.foodadvisor.dto.dialogue.DialogueMessageResponse;
import com.foodadvisor.service.DiningDialogueMessageService;
import com.foodadvisor.service.DialogueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            DialogueContinueRequest request
    ) {
        DialogueContinueResponse response =
                dialogueService.continueDialogue(
                        sessionId,
                        request.getUserId(),
                        request.getMessage()
                );

        return ApiResponse.success(response);
    }

    @PostMapping("/{sessionId}/messages")
    public ApiResponse<DialogueMessageResponse>
            sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody
            DialogueMessageRequest request
    ) {
        DialogueMessageResponse response =
                diningDialogueMessageService.sendMessage(
                        sessionId,
                        request
                );

        return ApiResponse.success(response);
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<DialogueHistoryResponse>
            listMessages(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Long userId
    ) {
        DialogueHistoryResponse response =
                diningDialogueMessageService.listMessages(
                        sessionId,
                        userId
                );

        return ApiResponse.success(response);
    }
}
