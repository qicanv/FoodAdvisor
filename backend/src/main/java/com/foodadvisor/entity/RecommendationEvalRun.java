package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 一次推荐评测批量运行。
 */
@Data
@TableName(value = "recommendation_eval_runs", autoResultMap = true)
public class RecommendationEvalRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private String status;

    private String modelName;

    private String modelVersion;

    private String promptVersion;

    private String algorithmVersion;

    private String dataVersion;

    private Integer requestedCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer uniqueMerchantCount;

    /**
     * 运行级汇总指标，JSONB。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metrics;

    private String errorMessage;

    private Long createdBy;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}