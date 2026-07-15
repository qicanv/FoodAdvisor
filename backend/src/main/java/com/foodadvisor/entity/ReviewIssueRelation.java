package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 差评归因关联表（V0.3 新增）
 *
 * 一条差评可关联多个问题类别
 */
@Data
@TableName("review_issue_relations")
public class ReviewIssueRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;

    /** 评价版本号 */
    private Integer reviewVersion;

    private Long issueCategoryId;

    /** 归因置信度 0-1 */
    private BigDecimal confidence;

    /** 原文依据片段 */
    private String evidenceText;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
