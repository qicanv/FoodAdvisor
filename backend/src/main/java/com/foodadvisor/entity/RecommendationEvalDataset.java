package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 推荐评测测试集。
 */
@Data
@TableName("recommendation_eval_datasets")
public class RecommendationEvalDataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String dataVersion;

    private String status;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}