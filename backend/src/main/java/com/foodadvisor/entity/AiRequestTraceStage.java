package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName(value = "ai_request_trace_stages", autoResultMap = true)
public class AiRequestTraceStage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String stageName;
    private Integer sequenceNo;
    private Integer attemptNo;
    private String status;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String inputSummary;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String outputSummary;
    private String provider;
    private String modelName;
    private String modelVersion;
    private String promptVersion;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
