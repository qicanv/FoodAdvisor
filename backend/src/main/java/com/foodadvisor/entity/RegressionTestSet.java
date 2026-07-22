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
        value = "regression_test_sets",
        autoResultMap = true
)
public class RegressionTestSet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String testType;

    private String dataVersion;

    private String status;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}