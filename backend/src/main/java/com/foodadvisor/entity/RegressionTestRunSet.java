package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("regression_test_run_sets")
public class RegressionTestRunSet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Long testSetId;

    private String testType;

    private String status;

    private String modelName;

    private String modelVersion;

    private String promptVersion;

    private String algorithmVersion;

    private String dataVersion;

    private Integer requestedCaseCount;

    private Integer completedCaseCount;

    private Integer passedCount;

    private Integer assertionFailedCount;

    private Integer executionErrorCount;

    private BigDecimal progressPercent;

    private String errorMessage;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}