package com.foodadvisor.dto.session;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatSessionCreateRequest {

    @Size(
            max = 200,
            message = "title不能超过200个字符"
    )
    private String title;
}