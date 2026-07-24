package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.BehaviorAnalysisService;
import com.foodadvisor.service.UserBehaviorService;
import com.foodadvisor.entity.UserBehaviorLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/behavior")
public class BehaviorAnalysisController {

    private final BehaviorAnalysisService behaviorAnalysisService;
    private final AdminAccessGuard adminAccessGuard;
    private final UserBehaviorService userBehaviorService;

    public BehaviorAnalysisController(BehaviorAnalysisService behaviorAnalysisService, 
                                     AdminAccessGuard adminAccessGuard,
                                     UserBehaviorService userBehaviorService) {
        this.behaviorAnalysisService = behaviorAnalysisService;
        this.adminAccessGuard = adminAccessGuard;
        this.userBehaviorService = userBehaviorService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> getBehaviorOverview(
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        if (startTime == null) {
            startTime = OffsetDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = OffsetDateTime.now();
        }

        return ApiResponse.success(behaviorAnalysisService.getBehaviorOverview(startTime, endTime));
    }

    @GetMapping("/hot-keywords")
    public ApiResponse<Map<String, Object>> getHotSearchKeywords(
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime,
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        if (startTime == null) {
            startTime = OffsetDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = OffsetDateTime.now();
        }

        return ApiResponse.success(behaviorAnalysisService.getHotSearchKeywords(startTime, endTime, limit));
    }

    @GetMapping("/hot-scenarios")
    public ApiResponse<Map<String, Object>> getHotScenarios(
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime,
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        if (startTime == null) {
            startTime = OffsetDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = OffsetDateTime.now();
        }

        return ApiResponse.success(behaviorAnalysisService.getHotScenarios(startTime, endTime, limit));
    }

    @GetMapping("/hot-merchants")
    public ApiResponse<Map<String, Object>> getHotMerchants(
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime,
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        if (startTime == null) {
            startTime = OffsetDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = OffsetDateTime.now();
        }

        return ApiResponse.success(behaviorAnalysisService.getHotMerchants(startTime, endTime, limit));
    }

    @GetMapping("/recommendation-stats")
    public ApiResponse<Map<String, Object>> getRecommendationStats(
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        if (startTime == null) {
            startTime = OffsetDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = OffsetDateTime.now();
        }

        return ApiResponse.success(behaviorAnalysisService.getRecommendationStats(startTime, endTime));
    }

    @PostMapping("/event")
    public ApiResponse<Map<String, Object>> reportBehaviorEvent(
            @RequestBody Map<String, Object> eventData) {

        Map<String, Object> result = behaviorAnalysisService.reportBehaviorEvent(eventData);

        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.failure("EVENT_REPORT_FAILED", (String) result.get("message"));
        }
    }

    @GetMapping("/stats")
    public ApiResponse<Object> getStats(
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        if (startTime == null) {
            startTime = OffsetDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = OffsetDateTime.now();
        }

        return ApiResponse.success(userBehaviorService.getStats(startTime, endTime));
    }

    @GetMapping("/logs")
    public ApiResponse<List<UserBehaviorLog>> getLogs(
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime,
            @RequestParam(required = false) String eventType,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        if (startTime == null) {
            startTime = OffsetDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = OffsetDateTime.now();
        }

        return ApiResponse.success(userBehaviorService.getEventLogs(startTime, endTime, eventType));
    }
}