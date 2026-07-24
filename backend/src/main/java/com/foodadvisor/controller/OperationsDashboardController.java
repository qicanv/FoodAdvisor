package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.OperationsDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class OperationsDashboardController {

    private final OperationsDashboardService operationsDashboardService;
    private final AdminAccessGuard adminAccessGuard;

    public OperationsDashboardController(
            OperationsDashboardService operationsDashboardService,
            AdminAccessGuard adminAccessGuard
    ) {
        this.operationsDashboardService = operationsDashboardService;
        this.adminAccessGuard = adminAccessGuard;
    }

    @GetMapping("/overview")
    public ApiResponse<Object> getOverview(
            @RequestParam(defaultValue = "week") String timeRange,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String month,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);
        return ApiResponse.success(operationsDashboardService.getOverview(timeRange, date, startDate, endDate, month));
    }

    @GetMapping("/trends")
    public ApiResponse<Object> getTrends(
            @RequestParam(defaultValue = "week") String timeRange,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String month,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);
        return ApiResponse.success(operationsDashboardService.getTrends(timeRange, date, startDate, endDate, month));
    }
}