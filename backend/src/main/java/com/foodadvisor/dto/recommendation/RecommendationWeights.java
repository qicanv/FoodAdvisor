package com.foodadvisor.dto.recommendation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 推荐排序权重。
 *
 * 每一项表示该评分项在最终100分中的最高得分。
 * 所有权重之和必须等于100。
 */
@Data
public class RecommendationWeights {

    @NotNull(message = "cuisine权重不能为空")
    @DecimalMin(
            value = "0.0",
            inclusive = true,
            message = "cuisine权重不能小于0"
    )
    private BigDecimal cuisine = new BigDecimal("25");

    @NotNull(message = "rating权重不能为空")
    @DecimalMin(
            value = "0.0",
            inclusive = true,
            message = "rating权重不能小于0"
    )
    private BigDecimal rating = new BigDecimal("20");

    @NotNull(message = "price权重不能为空")
    @DecimalMin(
            value = "0.0",
            inclusive = true,
            message = "price权重不能小于0"
    )
    private BigDecimal price = new BigDecimal("20");

    @NotNull(message = "distance权重不能为空")
    @DecimalMin(
            value = "0.0",
            inclusive = true,
            message = "distance权重不能小于0"
    )
    private BigDecimal distance = new BigDecimal("15");

    @NotNull(message = "environment权重不能为空")
    @DecimalMin(
            value = "0.0",
            inclusive = true,
            message = "environment权重不能小于0"
    )
    private BigDecimal environment = new BigDecimal("10");

    @NotNull(message = "reputation权重不能为空")
    @DecimalMin(
            value = "0.0",
            inclusive = true,
            message = "reputation权重不能小于0"
    )
    private BigDecimal reputation = new BigDecimal("10");

    @JsonIgnore
    public BigDecimal totalWeight() {
        return safe(cuisine)
                .add(safe(rating))
                .add(safe(price))
                .add(safe(distance))
                .add(safe(environment))
                .add(safe(reputation));
    }

    @AssertTrue(message = "推荐权重总和必须等于100")
    @JsonIgnore
    public boolean isTotalWeightValid() {
        if (cuisine == null
                || rating == null
                || price == null
                || distance == null
                || environment == null
                || reputation == null) {
            return false;
        }

        return totalWeight().compareTo(new BigDecimal("100")) == 0;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}