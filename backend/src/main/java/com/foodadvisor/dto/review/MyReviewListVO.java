package com.foodadvisor.dto.review;

import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class MyReviewListVO {

    private Long id;

    private Long merchantId;

    private String merchantName;

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
}