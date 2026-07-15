package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("review_analysis")
public class ReviewAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;

    /** 评价版本号 */
    private Integer reviewVersion;

    /** 分析版本号（同版本评价可多次分析） */
    private Integer analysisVersion;

    /** POSITIVE / NEUTRAL / NEGATIVE / MIXED */
    private String sentiment;

    private BigDecimal confidence;
    private Boolean lowConfidence;

    /** JSONB — 关键词数组 */
    private String keywords;

    /** JSONB — 方面级情感数组 */
    private String aspects;

    private String negativeReason;
    private String modelName;
    private String modelVersion;

    /** AI 调用追踪ID */
    private String businessTraceId;

    /** PENDING / SUCCESS / FAILED */
    private String status;

    private String errorMessage;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
