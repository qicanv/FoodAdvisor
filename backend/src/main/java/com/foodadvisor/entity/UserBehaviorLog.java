package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("user_behavior_logs")
public class UserBehaviorLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private Long userId;

    private String eventType;

    private String searchKeyword;

    private Long merchantId;

    private String sceneType;

    private Long topicId;

    private String tagCode;

    private String feedbackType;

    private Integer feedbackScore;

    private String pageUrl;

    private String referrerUrl;

    private String userAgent;

    private String ipAddress;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
