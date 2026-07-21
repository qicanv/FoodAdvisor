package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("moderation_actions")
public class ModerationAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long operatorUserId;

    private String action;

    private String previousStatus;

    private String newStatus;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}