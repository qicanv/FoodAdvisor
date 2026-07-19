package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.recommendation.RecommendationEvidenceDetailVO;
import com.foodadvisor.service.RecommendationEvidenceService;
import com.foodadvisor.util.AuthenticatedUserId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/diner/recommendations")
public class RecommendationEvidenceController {
    private final RecommendationEvidenceService service;

    public RecommendationEvidenceController(RecommendationEvidenceService service) {
        this.service = service;
    }

    @GetMapping("/{recommendationId}/evidences")
    public ApiResponse<List<RecommendationEvidenceDetailVO>> list(
            @PathVariable Long recommendationId,
            @RequestParam Long merchantId,
            HttpServletRequest request) {
        return ApiResponse.success(service.list(
                AuthenticatedUserId.require(request),
                recommendationId,
                merchantId));
    }
}
