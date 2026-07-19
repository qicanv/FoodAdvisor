package com.foodadvisor.dto.recommendation;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MatchedDishVO {

    private Long dishId;
    private Long merchantId;
    private String dishName;
    private BigDecimal dishPrice;
    private String category;
    private String matchType;
    private String matchedKeyword;
    private String matchReason;
    private BigDecimal matchScore;
}
