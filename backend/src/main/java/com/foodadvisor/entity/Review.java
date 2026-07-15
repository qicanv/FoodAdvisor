package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("reviews")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private Long userId;
    private Long importTaskId;
    private String externalId;
    private String sourceUserKey;
    private BigDecimal rating;
    private String content;
    private String source;           // SYSTEM / USER
    private LocalDateTime reviewTime;

    /** DRAFT / PUBLISHED / HIDDEN / DELETED */
    private String status;

    /** PENDING / APPROVED / REJECTED */
    private String moderationStatus;

    /** LOW / MEDIUM / HIGH */
    private String riskLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
