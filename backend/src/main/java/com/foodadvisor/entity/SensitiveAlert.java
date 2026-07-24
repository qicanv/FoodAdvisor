package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("sensitive_alerts")
public class SensitiveAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String topicType;

    private String riskLevel;

    private Integer reviewCount;

    /** JSONB - 主要关键词数组 */
    private String keywords;

    private OffsetDateTime firstOccurredAt;

    private OffsetDateTime lastOccurredAt;

    private String status;

    private Long handledBy;

    private OffsetDateTime handledAt;

    private String handledUsername;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
