package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(
        value = "regression_test_case_results",
        autoResultMap = true
)
public class RegressionTestCaseResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long runSetId;

    private Long caseId;

    private Long baselineResultId;

    private String caseCodeSnapshot;

    private String testType;

    private String executionStatus;

    private String assertionStatus;

    private String comparisonStatus;

    private String modelName;

    private String modelVersion;

    private String promptVersion;

    private String algorithmVersion;

    private String dataVersion;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String inputSnapshot;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String expectedSnapshot;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String actualOutput;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metrics;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String failureReasons;

    private String traceId;

    private Long durationMs;

    private String errorMessage;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}