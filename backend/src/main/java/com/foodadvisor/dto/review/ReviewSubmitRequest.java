package com.foodadvisor.dto.review;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReviewSubmitRequest {

    private String content;
    private Integer rating;
    private Integer tasteRating;
    private Integer environmentRating;
    private Integer serviceRating;
    private BigDecimal averageSpend;
    private LocalDate consumptionDate;
    private List<String> tags = new ArrayList<>();
    private List<Long> keepImageIds = new ArrayList<>();
}
