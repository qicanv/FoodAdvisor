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
 * 单个评测案例在一次运行中的结果。
 */
@Data
@TableName(value = "recommendation_eval_case_results", autoResultMap = true)
public class RecommendationEvalCaseResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long caseId;

    private String status;

    private String traceId;

    private String inputSnapshot;

    /**
     * 测试案例期望条件快照，JSONB。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String expectedConstraints;

    /**
     * 本轮提取条件，JSONB。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String extractedConstraints;

    /**
     * 合并后的结构化条件，JSONB。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String mergedConstraints;

    /**
     * 推荐商家结果快照，JSONB 数组。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String recommendationSnapshot;

    /**
     * 各项硬性条件指标，JSONB。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String hardConditionMetrics;

    /**
     * 失败原因列表，JSONB 数组。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String failureReasons;

    private Integer resultCount;

    private Long durationMs;

    private String errorMessage;

    private String relevanceLabel;

    private String annotationNote;

    private Long annotatedBy;

    private OffsetDateTime annotatedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}