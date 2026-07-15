package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_messages")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** USER / ASSISTANT / SYSTEM */
    private String role;

    private String content;

    /** TEXT / QUESTION / RECOMMENDATION / ERROR / SYSTEM_NOTICE */
    private String messageType;

    private String requestId;

    /** JSONB — 扩展元数据 */
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
