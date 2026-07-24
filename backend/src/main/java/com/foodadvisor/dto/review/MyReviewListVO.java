package com.foodadvisor.dto.review;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class MyReviewListVO {

    private Long id;

    private Long merchantId;

    private String merchantName;

    /**
     * ORIGINAL / FOLLOW_UP
     */
    private String reviewType;

    /**
     * 追评关联的原评价 ID。
     * 原评价通常为 null。
     */
    private Long parentReviewId;

    private BigDecimal rating;

    private String content;

    private String contentSummary;

    private OffsetDateTime publishedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String status;

    private String statusText;

    private Integer currentVersion;

    private Boolean hasReply;

    /**
     * 原评价下关联的有效追评。
     * 不存在追评时为 null。
     */
    private ReviewFollowUpVO followUp;
}