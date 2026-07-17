package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("review_reply")
public class ReviewReply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;
    private Long merchantId;
    private String replyContent;
    private OffsetDateTime replyTime;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}