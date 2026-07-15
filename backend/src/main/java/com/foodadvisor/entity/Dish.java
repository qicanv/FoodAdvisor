package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("dishes")
public class Dish {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private String name;
    private BigDecimal price;
    private String category;
    private String description;

    /** JSONB — 口味标签数组 */
    private String tasteTags;

    private String imageUrl;
    private Boolean recommended;

    /** ACTIVE / OFF_SHELF / ARCHIVED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}
