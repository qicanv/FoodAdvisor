package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.response.RegionalHotspotDTO;
import com.foodadvisor.service.RegionalHotspotService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/regional-hotspots")
@RequiredArgsConstructor
public class RegionalHotspotController {

    private final RegionalHotspotService regionalHotspotService;

    private boolean hasAdminAccess(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        String userRole = role == null ? null : role.toString();
        return "ADMIN".equalsIgnoreCase(userRole) || "OPERATOR".equalsIgnoreCase(userRole);
    }

    @GetMapping("/regions")
    public ApiResponse<List<Map<String, Object>>> getAllRegions(HttpServletRequest request) {
        if (!hasAdminAccess(request)) {
            return ApiResponse.failure("FORBIDDEN", "无管理权限");
        }

        List<Map<String, Object>> regions = regionalHotspotService.getAllRegions();
        return ApiResponse.success(regions);
    }

    @GetMapping
    public ApiResponse<RegionalHotspotDTO> getRegionalHotspots(
            HttpServletRequest request,
            @RequestParam(value = "regionCode", defaultValue = "CD") String regionCode,
            @RequestParam(value = "startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(value = "endTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {

        if (!hasAdminAccess(request)) {
            return ApiResponse.failure("FORBIDDEN", "无管理权限");
        }

        RegionalHotspotDTO result = regionalHotspotService.getRegionalHotspots(regionCode, startTime, endTime);
        return ApiResponse.success(result);
    }
}
