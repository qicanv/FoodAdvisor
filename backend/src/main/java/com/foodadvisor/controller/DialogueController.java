package com.foodadvisor.controller;

import com.foodadvisor.backend.common.ApiResponse;
import com.foodadvisor.dto.dialogue.DialogueContinueRequest;
import com.foodadvisor.dto.dialogue.DialogueContinueResponse;
import com.foodadvisor.service.DialogueService;
import jakarta.validation.Valid;
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

    public DialogueController(
            DialogueService dialogueService
    ) {
        this.dialogueService = dialogueService;
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
}