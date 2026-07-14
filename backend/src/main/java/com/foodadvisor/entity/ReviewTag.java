package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 评论标签字典表
 */
@Data
@TableName("review_tags")
public class ReviewTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签编码，如 TASTE_GOOD, ENVIRONMENT_QUIET */
    private String code;

    /** 标签名称，如 "口味好", "环境安静" */
    private String name;

    /** 标签类别：TASTE/ENVIRONMENT/SERVICE/PRICE/QUEUE_TIME/HYGIENE/PORTION/SPEED/PARKING */
    private String category;

    /** ACTIVE / DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
