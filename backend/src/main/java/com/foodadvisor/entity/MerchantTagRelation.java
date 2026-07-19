package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("merchant_tag_relations")
public class MerchantTagRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private Long tagId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}