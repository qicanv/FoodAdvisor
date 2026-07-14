package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("merchant_business_hours")
public class MerchantBusinessHours {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    /** 1=周一, 7=周日 */
    private Integer dayOfWeek;

    private LocalTime openTime;
    private LocalTime closeTime;
    private Boolean isClosed;
    private Boolean crossesMidnight;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
