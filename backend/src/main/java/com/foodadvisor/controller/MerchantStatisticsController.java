package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.service.MerchantStatisticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant-statistics")
public class MerchantStatisticsController {

    private final MerchantStatisticsService statisticsService;

    public MerchantStatisticsController(MerchantStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public ApiResponse<Object> getOverview(
            @RequestParam(defaultValue = "week") String timeRange,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String week,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long merchantId,
            HttpServletRequest request
    ) {
        String role = (String) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");

        if ("ADMIN".equalsIgnoreCase(role)) {
            return ApiResponse.success(statisticsService.getOverview(timeRange, date, week, month, role, null));
        } else if ("MERCHANT".equalsIgnoreCase(role)) {
            Long effectiveMerchantId = merchantId != null ? merchantId : userId;
            return ApiResponse.success(statisticsService.getOverview(timeRange, date, week, month, role, effectiveMerchantId));
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only administrators and merchants can access merchant statistics");
        }
    }

    @GetMapping("/trends")
    public ApiResponse<Object> getTrends(
            @RequestParam(defaultValue = "week") String timeRange,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String week,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long merchantId,
            HttpServletRequest request
    ) {
        String role = (String) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");

        if ("ADMIN".equalsIgnoreCase(role)) {
            return ApiResponse.success(statisticsService.getTrends(timeRange, date, week, month, role, null));
        } else if ("MERCHANT".equalsIgnoreCase(role)) {
            Long effectiveMerchantId = merchantId != null ? merchantId : userId;
            return ApiResponse.success(statisticsService.getTrends(timeRange, date, week, month, role, effectiveMerchantId));
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only administrators and merchants can access merchant statistics");
        }
    }
}