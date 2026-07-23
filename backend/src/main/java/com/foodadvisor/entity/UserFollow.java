package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("user_follows")
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long merchantId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}