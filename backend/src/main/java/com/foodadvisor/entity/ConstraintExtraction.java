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
@TableName(value = "constraint_extractions", autoResultMap = true)
public class ConstraintExtraction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long messageId;

    /**
     * JSONB — 本轮从用户消息中提取出的条件
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String extractedConstraints;

    /**
     * JSONB — 与旧会话状态合并后的条件
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String mergedConstraints;

    /**
     * JSONB — 本轮发生变化的字段名称
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String changedFields;

    /**
     * JSONB — 本轮检测出的冲突字段
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String conflictFields;

    private String modelName;

    private String modelVersion;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}