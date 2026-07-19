package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("topics")
public class Topic {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    private String coverUrl;
    private String status;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}