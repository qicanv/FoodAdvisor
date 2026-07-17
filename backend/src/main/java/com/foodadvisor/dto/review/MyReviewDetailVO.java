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

    @Data
    public static class ReviewVersionVO {
        private Integer version;
        private BigDecimal rating;
        private String content;
        private String changeType;
        private OffsetDateTime createdAt;
    }
}