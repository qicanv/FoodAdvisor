package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("topic_tags")
public class TopicTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long topicId;
    private Long tagId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}