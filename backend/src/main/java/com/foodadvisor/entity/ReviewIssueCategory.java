package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 差评归因类别字典表（V0.3 新增）
 *
 * 内置类别：HYGIENE / SERVICE_ATTITUDE / SERVING_SPEED /
 *           TASTE / PRICE / PORTION / QUEUE / ENVIRONMENT / OTHER
 */
@Data
@TableName("review_issue_categories")
public class ReviewIssueCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类别编码，如 HYGIENE */
    private String code;

    /** 类别名称，如 卫生问题 */
    private String name;

    private String description;

    /** ACTIVE / DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
