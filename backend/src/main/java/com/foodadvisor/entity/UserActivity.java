package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("user_activities")
public class UserActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String activityType;
    private String targetType;
    private Long targetId;
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}