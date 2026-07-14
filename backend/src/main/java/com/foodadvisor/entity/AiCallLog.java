package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_call_logs")
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
    private String requestSummary;

    /** JSONB */
    private String responseSummary;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
