package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("rate_limit_events")
public class RateLimitEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleName;
    private String keyType;
    private String subjectValue;
    private Long userId;
    private String clientIp;
    private String requestMethod;
    private String requestPath;
    private Integer limitCount;
    private Integer windowSeconds;
    private Long currentCount;
    private Integer retryAfterSeconds;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
