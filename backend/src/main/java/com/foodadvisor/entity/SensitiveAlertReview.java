package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("sensitive_alert_reviews")
public class SensitiveAlertReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long alertId;

    private Long reviewId;

    private Integer reviewVersion;

    private String evidenceExcerpt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
