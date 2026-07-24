package com.foodadvisor.dto.review;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class MyReviewDetailVO {

    private Long id;

    private Long merchantId;

    /**
     * ORIGINAL / FOLLOW_UP
     */
    private String reviewType;

    /**
     * 追评关联的原评价 ID。
     * 原评价通常为 null。
     */
    private Long parentReviewId;

    private String merchantName;

    private String merchantCategory;

    private String merchantCuisine;

    private BigDecimal rating;

    private BigDecimal tasteRating;

    private BigDecimal environmentRating;

    private BigDecimal serviceRating;

    private BigDecimal averageSpend;

    private LocalDate consumptionDate;

    private String content;

    private String status;

    private String statusText;

    private String moderationStatus;

    private Integer currentVersion;

    private OffsetDateTime publishedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime editedAt;

    private List<ReviewImageVO> images;

    private List<ReviewVersionVO> versionHistory;

    private ReviewReplyVO merchantReply;

    /**
     * 原评价关联的有效追评。
     * 没有追评时为 null。
     */
    private ReviewFollowUpVO followUp;

    @Data
    public static class ReviewVersionVO {
        private Integer version;
        private BigDecimal rating;
        private String content;
        private String changeType;
        private OffsetDateTime createdAt;
    }
}