package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("review_rule_matches")
public class ReviewRuleMatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;
    private String ruleCode;
    private String keyword;
    private Integer matchPosition;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}