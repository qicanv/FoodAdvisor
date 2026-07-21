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
 * 推荐评测测试案例。
 */
@Data
@TableName(value = "recommendation_eval_cases", autoResultMap = true)
public class RecommendationEvalCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private String caseCode;

    private String caseName;

    private String inputText;

    /**
     * 期望识别出的结构化条件，JSONB。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String expectedConstraints;

    /**
     * 用户经纬度、区域等位置信息快照，JSONB。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String locationSnapshot;

    /**
     * 案例标签，JSONB 数组。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String tags;

    private Integer sequenceNo;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}