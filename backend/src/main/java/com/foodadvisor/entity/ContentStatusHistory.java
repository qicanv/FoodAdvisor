package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 内容状态变更历史
 * 对应数据库表 content_status_history
 */
@Data
@TableName("content_status_history")
public class ContentStatusHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 内容类型：MERCHANT / DISH / TOPIC / KNOWLEDGE */
    private String contentType;

    /** 内容主键ID */
    private Long contentId;

    /** 变更前状态 */
    private String oldStatus;

    /** 变更后状态 */
    private String newStatus;

    /** 操作人员用户ID */
    private Long operatorUserId;

    /** 变更原因 */
    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
