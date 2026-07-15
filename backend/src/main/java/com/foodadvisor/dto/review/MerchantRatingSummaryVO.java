package com.foodadvisor.dto.review;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantRatingSummaryVO {

    private BigDecimal averageRating;
    private BigDecimal averageTasteRating;
    private BigDecimal averageEnvironmentRating;
    private BigDecimal averageServiceRating;
    private Long ratingCount;
}
