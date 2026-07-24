package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.BehaviorAnalysisService;
import com.foodadvisor.service.UserBehaviorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        OffsetDateTime start = parseDateTime(startTime);
        OffsetDateTime end = parseDateTime(endTime);

        if (start == null) {
            start = OffsetDateTime.now().minusDays(7);
        }
        if (end == null) {
            end = OffsetDateTime.now();
        }

        return ApiResponse.success(userBehaviorService.getStats(start, end));
    }

    @GetMapping("/logs")
    public ApiResponse<Object> getLogs(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        OffsetDateTime start = parseDateTime(startTime);
        OffsetDateTime end = parseDateTime(endTime);

        if (start == null) {
            start = OffsetDateTime.now().minusDays(7);
        }
        if (end == null) {
            end = OffsetDateTime.now();
        }

        if (pageNum != null && pageSize != null) {
            return ApiResponse.success(userBehaviorService.getEventLogsWithPagination(start, end, eventType, pageNum, pageSize));
        }
        return ApiResponse.success(userBehaviorService.getEventLogs(start, end, eventType));
    }

    private OffsetDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            if (dateTimeStr.endsWith("Z")) {
                return OffsetDateTime.parse(dateTimeStr);
            }
            return OffsetDateTime.parse(dateTimeStr + "Z");
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateTimeStr).atOffset(ZoneOffset.UTC);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}