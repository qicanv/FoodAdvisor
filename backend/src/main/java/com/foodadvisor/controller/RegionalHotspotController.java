package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.response.RegionalHotspotDTO;
import com.foodadvisor.service.RegionalHotspotService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String week,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {

        if (!hasAdminAccess(request)) {
            return ApiResponse.failure("FORBIDDEN", "无管理权限");
        }

        OffsetDateTime start = null;
        OffsetDateTime end = null;
        
        if (timeRange != null && !timeRange.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            
            switch (timeRange) {
                case "day":
                    if (date != null) {
                        LocalDateTime dateTime = LocalDateTime.parse(date);
                        start = dateTime.atOffset(ZoneOffset.UTC);
                        end = dateTime.plusDays(1).atOffset(ZoneOffset.UTC);
                    } else {
                        start = now.atOffset(ZoneOffset.UTC);
                        end = now.plusDays(1).atOffset(ZoneOffset.UTC);
                    }
                    break;
                case "week":
                    if (week != null) {
                        String[] weekParts = week.split("-W");
                        int year = Integer.parseInt(weekParts[0]);
                        int weekNum = Integer.parseInt(weekParts[1]);
                        LocalDateTime weekStart = LocalDateTime.of(year, 1, 1, 0, 0);
                        int dayOfWeek = weekStart.getDayOfWeek().getValue();
                        if (dayOfWeek != 1) {
                            weekStart = weekStart.minusDays(dayOfWeek - 1);
                        }
                        weekStart = weekStart.plusWeeks(weekNum - 1);
                        start = weekStart.atOffset(ZoneOffset.UTC);
                        end = weekStart.plusWeeks(1).atOffset(ZoneOffset.UTC);
                    } else {
                        LocalDateTime weekStart = now.minusDays(now.getDayOfWeek().getValue() - 1);
                        start = weekStart.atOffset(ZoneOffset.UTC);
                        end = weekStart.plusWeeks(1).atOffset(ZoneOffset.UTC);
                    }
                    break;
                case "month":
                    if (month != null) {
                        String[] monthParts = month.split("-");
                        int year = Integer.parseInt(monthParts[0]);
                        int monthNum = Integer.parseInt(monthParts[1]);
                        LocalDateTime monthStart = LocalDateTime.of(year, monthNum, 1, 0, 0);
                        start = monthStart.atOffset(ZoneOffset.UTC);
                        end = monthStart.plusMonths(1).atOffset(ZoneOffset.UTC);
                    } else {
                        LocalDateTime monthStart = LocalDateTime.of(now.getYear(), now.getMonth(), 1, 0, 0);
                        start = monthStart.atOffset(ZoneOffset.UTC);
                        end = monthStart.plusMonths(1).atOffset(ZoneOffset.UTC);
                    }
                    break;
                default:
                    start = OffsetDateTime.parse("2020-01-01T00:00:00Z");
                    end = OffsetDateTime.now();
            }
        } else {
            if (startTime == null) {
                start = OffsetDateTime.now().minusDays(7);
            } else {
                start = startTime;
            }
            if (endTime == null) {
                end = OffsetDateTime.now();
            } else {
                end = endTime;
            }
        }

        RegionalHotspotDTO result = regionalHotspotService.getRegionalHotspots(regionCode, start, end);
        return ApiResponse.success(result);
    }
}
