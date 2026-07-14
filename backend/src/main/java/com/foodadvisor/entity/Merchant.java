package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    /** OPEN / CLOSED / TEMPORARILY_CLOSED */
    private String businessStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
