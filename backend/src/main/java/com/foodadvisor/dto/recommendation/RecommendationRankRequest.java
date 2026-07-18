package com.foodadvisor.dto.recommendation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 推荐排序请求。
 *
 * sessionId 通过接口路径传入。
 * 当前会话约束由服务端读取，不允许客户端重复提交。
 */
@Data
public class RecommendationRankRequest {

    /**
     * 当前操作用户，用于校验会话归属。
     */
    private Long userId;

    /**
     * 用户纬度，可选。
     * 不提供位置时，距离项不参与实际距离计算。
     */
    @DecimalMin(
            value = "-90.0",
            message = "userLatitude不能小于-90"
    )
    @DecimalMax(
            value = "90.0",
            message = "userLatitude不能大于90"
    )
    private BigDecimal userLatitude;

    /**
     * 用户经度，可选。
     */
    @DecimalMin(
            value = "-180.0",
            message = "userLongitude不能小于-180"
    )
    @DecimalMax(
            value = "180.0",
            message = "userLongitude不能大于180"
    )
    private BigDecimal userLongitude;

    /**
     * 本次排序使用的权重。
     *
     * 请求未传该字段时使用默认权重。
     */
    @Valid
    @NotNull(message = "weights不能为空")
    private RecommendationWeights weights =
            new RecommendationWeights();

    /**
     * 经纬度必须同时提供，不能只提供其中一个。
     */
    @AssertTrue(message = "userLatitude和userLongitude必须同时提供或同时不提供")
    @JsonIgnore
    public boolean isLocationPairValid() {
        return (userLatitude == null && userLongitude == null)
                || (userLatitude != null && userLongitude != null);
    }
}
