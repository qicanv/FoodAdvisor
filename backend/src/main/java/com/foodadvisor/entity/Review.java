package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@TableName("reviews")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private Long userId;
    private Long importTaskId;

    /** ORIGINAL / FOLLOW_UP */
    private String reviewType;

    /** 追评关联原评价ID */
    private Long parentReviewId;

    /** 综合评分 1-5 */
    private BigDecimal rating;

    /** 口味评分 1-5 */
    private BigDecimal tasteRating;

    /** 环境评分 1-5 */
    private BigDecimal environmentRating;

    /** 服务评分 1-5 */
    private BigDecimal serviceRating;

    /** 人均消费 */
    private BigDecimal averageSpend;

    /** 消费日期 */
    private LocalDate consumptionDate;

    /** 评价正文 10-2000字 */
    private String content;

    /** SYSTEM / IMPORT */
    private String source;

    /** 评价时间（兼容原始种子数据） */
    private OffsetDateTime reviewTime;

    /** 外部导入编号 */
    private String externalId;

    /** 外部用户标识 */
    private String sourceUserKey;

    /** 幂等键 */
    private String idempotencyKey;

    /** 当前版本号 >= 1 */
    private Integer currentVersion;

    /** PENDING / PUBLISHED / HIDDEN / DELETED */
    private String status;

    /** PENDING / APPROVED / REJECTED */
    private String moderationStatus;

    /** LOW / MEDIUM / HIGH */
    private String riskLevel;

    private OffsetDateTime publishedAt;
    private OffsetDateTime editedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}
