package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@TableName("review_versions")
public class ReviewVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;
    private Integer version;
    private BigDecimal rating;
    private BigDecimal tasteRating;
    private BigDecimal environmentRating;
    private BigDecimal serviceRating;
    private BigDecimal averageSpend;
    private LocalDate consumptionDate;
    private String content;
    private String imageSnapshot;
    private String statusSnapshot;
    private String moderationStatusSnapshot;
    private Long changedBy;
    private String changeType;
    private OffsetDateTime createdAt;
}
