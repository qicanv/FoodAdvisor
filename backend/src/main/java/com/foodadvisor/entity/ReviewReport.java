package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 评价举报记录（EPIC-08 故事5）
 */
@Data
@TableName("review_reports")
public class ReviewReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 举报人用户ID */
    private Long reporterUserId;

    /** 被举报评价ID */
    private Long reportedReviewId;

    /** 商家ID */
    private Long merchantId;

    /** 举报原因：ADVERTISING / FALSE_REVIEW / MALICIOUS_ATTACK / SEXUAL_OR_VULGAR / PRIVACY_LEAK / OTHER */
    private String reason;

    /** 补充说明（最多500字） */
    private String description;

    /** 处理状态：PENDING / RESOLVED / REJECTED */
    private String status;

    /** 处理人ID */
    private Long handledBy;

    /** 处理时间 */
    private OffsetDateTime handledAt;

    /** 处理结果说明 */
    private String resolution;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
