package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("moderation_rules")
public class ModerationRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;
    private String ruleCode;
    private String description;
    private String riskLevel;
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}