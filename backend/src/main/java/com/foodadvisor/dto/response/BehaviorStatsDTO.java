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
public class BehaviorStatsDTO {

    private Long totalEvents;

    private Long activeUsers;

    private List<Map<String, Object>> hotKeywords;

    private List<Map<String, Object>> hotScenes;

    private List<Map<String, Object>> hotMerchants;

    private List<Map<String, Object>> eventStats;

    private List<Map<String, Object>> hotTags;

    private List<Map<String, Object>> dailyStats;
}
