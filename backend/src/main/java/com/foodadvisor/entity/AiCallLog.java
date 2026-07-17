package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName(value = "ai_call_logs", autoResultMap = true)
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;
    private Long userId;
    private Long sessionId;
    private String functionType;
    private String provider;
    private String modelName;
    private String modelVersion;

    /** SUCCESS / FAILED / TIMEOUT */
    private String status;

    private Integer latencyMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private BigDecimal estimatedCost;
    private String errorType;
    private String errorMessage;

    /** JSONB */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String requestSummary;

    /** JSONB */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String responseSummary;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
