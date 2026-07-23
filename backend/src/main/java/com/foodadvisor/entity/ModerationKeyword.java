package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("moderation_keywords")
public class ModerationKeyword {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleCode;
    private String keyword;
    private String matchType;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}