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
@TableName(value = "recommendations", autoResultMap = true)
public class Recommendation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sessionId;

    private Long userMessageId;

    private Long assistantMessageId;

    private String requestId;

    private String traceId;

    /**
     * 本次推荐请求说明。
     */
    private String queryText;

    /**
     * JSONB：本次推荐使用的完整结构化约束快照。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String parsedConstraints;

    /**
     * 推荐回复或推荐结果摘要。
     */
    private String replyText;

    /**
     * 排序算法版本，例如 RULE_V1。
     */
    private String algorithmVersion;

    /**
     * JSONB：本次实际使用的权重快照。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String weightSnapshot;

    private String modelName;

    private String modelVersion;

    /**
     * PENDING / SUCCESS / NO_MATCH / FAILED。
     */
    private String status;

    private Integer resultCount;

    private String errorCode;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime completedAt;
}