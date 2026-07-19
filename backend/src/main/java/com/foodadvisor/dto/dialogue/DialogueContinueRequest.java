package com.foodadvisor.dto.dialogue;

import jakarta.validation.constraints.NotBlank;
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
     * 兼容旧客户端；Controller 会使用 JWT 身份覆盖该值。
     */
    private Long userId;
}
