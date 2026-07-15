package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("merchants")
public class Merchant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantCode;
    private String name;
    private String category;
    private String cuisine;
    private BigDecimal rating;
    private BigDecimal averagePrice;
    private Integer reviewCount;
    private String address;
    private String regionCode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String description;

    /** JSONB — 环境标签数组，如 ["朋友聚会","环境舒适"] */
    private String environmentTags;

    /** ACTIVE / DISABLED / ARCHIVED */
    private String platformStatus;

    /** OPERATING / SUSPENDED / CLOSED_PERMANENTLY */
    private String operationStatus;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}
