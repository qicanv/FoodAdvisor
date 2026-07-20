package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "ai_request_traces", autoResultMap = true)
public class AiRequestTrace {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String requestId;
    private Long sessionId;
    private Long userId;
    private String scene;
    private String intent;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String structuredConditions;
    private String provider;
    private String modelName;
    private String modelVersion;
    private String promptVersion;
    private String status;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String finalOutputSummary;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private Long totalDurationMs;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
