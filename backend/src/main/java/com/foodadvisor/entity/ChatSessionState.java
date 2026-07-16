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
@TableName(value = "chat_session_states", autoResultMap = true)
public class ChatSessionState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /**
     * JSONB — 当前合并后的消费条件
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String currentConstraints;

    /**
     * JSONB — 缺失字段列表
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String missingFields;

    /**
     * JSONB — 用户拒绝回答的字段
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String rejectedFields;

    /**
     * JSONB — 待确认的冲突条件
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String pendingConfirmation;

    /**
     * COLLECTING / CONFIRMING / SEARCHING /
     * RECOMMENDED / COMPLETED
     */
    private String conversationStage;

    private Integer version;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}