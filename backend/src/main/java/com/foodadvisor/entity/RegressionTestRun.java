package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName(
        value = "regression_test_runs",
        autoResultMap = true
)
public class RegressionTestRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runName;

    private Long baselineRunId;

    private String status;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String modelVersions;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String promptVersions;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String algorithmVersions;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String dataVersions;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String requestSnapshot;

    private Integer requestedSetCount;

    private Integer completedSetCount;

    private Integer requestedCaseCount;

    private Integer completedCaseCount;

    private Integer passedCount;

    private Integer assertionFailedCount;

    private Integer executionErrorCount;

    private BigDecimal progressPercent;

    private String errorMessage;

    private Long createdBy;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}