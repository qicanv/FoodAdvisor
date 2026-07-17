package com.foodadvisor.dto.constraint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 消费需求提取接口的请求体。
 */
@Data
public class ConstraintExtractRequest {

    /**
     * 用户本轮输入的自然语言需求。
     */
    @NotBlank(message = "message不能为空")
    @Size(max = 2000, message = "message不能超过2000个字符")
    private String message;
}