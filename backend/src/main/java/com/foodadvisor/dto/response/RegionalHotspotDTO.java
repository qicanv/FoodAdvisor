package com.foodadvisor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionalHotspotDTO {

    private String regionCode;

    private String regionName;

    private List<Map<String, Object>> hotMerchants;

    private List<Map<String, Object>> hotCuisines;

    private List<Map<String, Object>> consumptionPeriods;

    private List<Map<String, Object>> trendChanges;

    private List<Map<String, Object>> dailyTrend;

    private Long totalEvents;

    private Long totalUsers;
}
