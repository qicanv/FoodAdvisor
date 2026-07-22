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
        value = "regression_test_cases",
        autoResultMap = true
)
public class RegressionTestCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long testSetId;

    private String caseCode;

    private String caseName;

    private String description;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String inputPayload;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String expectedOutput;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String assertionConfig;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String tags;

    private Integer sequenceNo;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}