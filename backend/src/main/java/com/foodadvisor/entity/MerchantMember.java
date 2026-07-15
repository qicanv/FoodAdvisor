package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("merchant_members")
public class MerchantMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private Long userId;

    /** OWNER / MANAGER / STAFF */
    private String memberRole;

    /** ACTIVE / DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
