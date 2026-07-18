package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.recommendation.RecommendationAdjustRequest;
import com.foodadvisor.dto.recommendation.RecommendationRankRequest;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import com.foodadvisor.service.RecommendationRankingService;
import com.foodadvisor.util.AuthenticatedUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家推荐排序接口。
 *
 * 排序约束从指定会话的 chat_session_states 中读取，
 * 客户端只需提供用户身份、可选位置和可选排序权重。
 */
@RestController
@RequestMapping("/api/diner/sessions")
public class RecommendationController {

    private final RecommendationRankingService
            recommendationRankingService;

    public RecommendationController(
            RecommendationRankingService
                    recommendationRankingService
    ) {
        this.recommendationRankingService =
                recommendationRankingService;
    }

    /**
     * 根据会话中的消费需求，对商家进行硬过滤和规则排序。
     */
    @PostMapping(
            "/{sessionId}/recommendations/rank"
    )
    public ApiResponse<RecommendationRankResponse>
    rankRecommendations(
            @PathVariable Long sessionId,
            @Valid
            @RequestBody
            RecommendationRankRequest request,
            HttpServletRequest httpRequest
    ) {
        request.setUserId(
                AuthenticatedUserId.require(httpRequest)
        );
        RecommendationRankResponse response =
                recommendationRankingService.rank(
                        sessionId,
                        request
                );

        return ApiResponse.success(response);
    }

    @PostMapping(
            "/{sessionId}/recommendations/adjust"
    )
    public ApiResponse<RecommendationRankResponse>
    adjustRecommendation(
            @PathVariable Long sessionId,
            @Valid
            @RequestBody
            RecommendationAdjustRequest request,
            HttpServletRequest httpRequest
    ) {
        request.setUserId(
                AuthenticatedUserId.require(httpRequest)
        );
        RecommendationRankResponse response =
                recommendationRankingService
                        .adjustAndRank(
                                sessionId,
                                request
                        );

        return ApiResponse.success(response);
    }
}
