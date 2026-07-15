package com.foodadvisor.dto.review;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReviewSubmitResponse {

    private Long id;
    private Long userId;
    private Long merchantId;
    private String content;
    private Integer rating;
    private Integer tasteRating;
    private Integer environmentRating;
    private Integer serviceRating;
    private BigDecimal averageSpend;
    private LocalDate consumptionDate;
    private Integer currentVersion;
    private String status;
    private String moderationStatus;
    private String riskLevel;
    private List<ReviewImageVO> images = new ArrayList<>();
}
