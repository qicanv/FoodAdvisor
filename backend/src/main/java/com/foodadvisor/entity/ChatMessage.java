package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "chat_messages", autoResultMap = true)
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /**
     * USER / ASSISTANT / SYSTEM
     */
    private String role;

    private String content;

    /**
     * TEXT / QUESTION / RECOMMENDATION /
     * ERROR / SYSTEM_NOTICE
     */
    private String messageType;

    private String requestId;

    /**
     * JSONB — 扩展元数据
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}