package com.foodadvisor.dto.dialogue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 继续多轮对话的请求参数。
 */
@Data
public class DialogueContinueRequest {

    /**
     * 用户本轮输入。
     */
    @NotBlank(message = "message不能为空")
    @Size(max = 2000, message = "message不能超过2000个字符")
    private String message;

    /**
     * 当前用户 ID。
     *
     * 当前项目尚未接入完整登录认证，
     * 暂时从请求体中传入。
     */
    @NotNull(message = "userId不能为空")
    private Long userId;
}