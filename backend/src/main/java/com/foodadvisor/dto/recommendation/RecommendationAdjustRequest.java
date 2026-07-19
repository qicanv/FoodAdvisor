package com.foodadvisor.dto.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecommendationAdjustRequest {

    private Long userId;

    @NotNull(message = "sourceMessageId不能为空")
    private Long sourceMessageId;

    @NotBlank(message = "field不能为空")
    private String field;

    @NotNull(message = "value不能为空")
    private Object value;

    @DecimalMin(value = "-90.0", message = "userLatitude不能小于-90")
    @DecimalMax(value = "90.0", message = "userLatitude不能大于90")
    private BigDecimal userLatitude;

    @DecimalMin(value = "-180.0", message = "userLongitude不能小于-180")
    @DecimalMax(value = "180.0", message = "userLongitude不能大于180")
    private BigDecimal userLongitude;

    @Valid
    private RecommendationWeights weights =
            new RecommendationWeights();
}
