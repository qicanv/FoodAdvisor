package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("recommendation_feedback")
public class RecommendationFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recommendationId;

    /** SATISFIED / DISSATISFIED */
    private String feedbackType;

    private String content;
    private String reasonCategory;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
