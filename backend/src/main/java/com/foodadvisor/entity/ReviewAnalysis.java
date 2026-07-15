package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName(value = "review_analysis", autoResultMap = true)
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
    @TableField(jdbcType = JdbcType.OTHER)
    private String keywords;

    /** JSONB — 方面级情感数组 */
    @TableField(jdbcType = JdbcType.OTHER)
    private String aspects;

    private String negativeReason;
    private String modelName;
    private String modelVersion;

    /** AI 调用追踪ID */
    private String businessTraceId;

    /** PENDING / SUCCESS / FAILED */
    private String status;

    private String errorMessage;

    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
