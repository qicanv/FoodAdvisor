package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("topic_merchants")
public class TopicMerchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long topicId;
    private Long merchantId;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}