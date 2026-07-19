package com.foodadvisor.dto.topic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicMerchantDTO {

    private Long id;
    private String merchantCode;
    private String name;
    private String category;
    private String cuisine;
    private BigDecimal rating;
    private BigDecimal averagePrice;
    private String operationStatus;
    private String description;
}