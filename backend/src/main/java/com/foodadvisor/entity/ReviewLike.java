package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("review_likes")
public class ReviewLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long reviewId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}