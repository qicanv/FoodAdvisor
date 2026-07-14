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

    /** PENDING / SUCCESS / FAILED */
    private String status;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime analyzedAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
