package com.foodadvisor.service;

import com.foodadvisor.dto.response.RegionalHotspotDTO;
import com.foodadvisor.mapper.UserBehaviorLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegionalHotspotService {

    private final UserBehaviorLogMapper behaviorLogMapper;

    public RegionalHotspotDTO getRegionalHotspots(String regionCode, OffsetDateTime startTime, OffsetDateTime endTime) {
        OffsetDateTime prevStartTime = startTime.minusDays(7);
        OffsetDateTime prevEndTime = endTime.minusDays(7);

        List<Map<String, Object>> hotMerchants = behaviorLogMapper.getRegionalHotMerchants(regionCode, startTime, endTime, 15);
        List<Map<String, Object>> hotCuisines = behaviorLogMapper.getRegionalHotCuisines(regionCode, startTime, endTime, 10);
        List<Map<String, Object>> hotKeywords = behaviorLogMapper.getRegionalHotKeywords(regionCode, startTime, endTime, 10);
        List<Map<String, Object>> consumptionPeriods = behaviorLogMapper.getRegionalConsumptionPeriods(regionCode, startTime, endTime);
        Long totalEvents = behaviorLogMapper.getRegionalTotalEvents(regionCode, startTime, endTime);
        Long totalUsers = behaviorLogMapper.getRegionalActiveUsers(regionCode, startTime, endTime);

        List<Map<String, Object>> trendChanges = calculateTrendChanges(
                regionCode, startTime, endTime, prevStartTime, prevEndTime);

        String regionName = getRegionName(regionCode);

        return RegionalHotspotDTO.builder()
                .regionCode(regionCode)
                .regionName(regionName)
                .hotMerchants(hotMerchants)
                .hotCuisines(hotCuisines)
                .hotKeywords(hotKeywords)
                .consumptionPeriods(consumptionPeriods)
                .trendChanges(trendChanges)
                .totalEvents(totalEvents)
                .totalUsers(totalUsers)
                .build();
    }

    private List<Map<String, Object>> calculateTrendChanges(String regionCode,
                                                             OffsetDateTime startTime, OffsetDateTime endTime,
                                                             OffsetDateTime prevStartTime, OffsetDateTime prevEndTime) {
        List<Map<String, Object>> trends = new ArrayList<>();

        Long currentMerchantClicks = behaviorLogMapper.getRegionalMerchantClicks(regionCode, startTime, endTime);
        Long prevMerchantClicks = behaviorLogMapper.getRegionalMerchantClicks(regionCode, prevStartTime, prevEndTime);
        trends.add(createTrendItem("商家点击", currentMerchantClicks, prevMerchantClicks));

        Long currentSearches = behaviorLogMapper.getRegionalSearches(regionCode, startTime, endTime);
        Long prevSearches = behaviorLogMapper.getRegionalSearches(regionCode, prevStartTime, prevEndTime);
        trends.add(createTrendItem("搜索次数", currentSearches, prevSearches));

        Long currentSceneEntries = behaviorLogMapper.getRegionalSceneEntries(regionCode, startTime, endTime);
        Long prevSceneEntries = behaviorLogMapper.getRegionalSceneEntries(regionCode, prevStartTime, prevEndTime);
        trends.add(createTrendItem("场景入口", currentSceneEntries, prevSceneEntries));

        Long currentTagClicks = behaviorLogMapper.getRegionalTagClicks(regionCode, startTime, endTime);
        Long prevTagClicks = behaviorLogMapper.getRegionalTagClicks(regionCode, prevStartTime, prevEndTime);
        trends.add(createTrendItem("标签点击", currentTagClicks, prevTagClicks));

        return trends;
    }

    private Map<String, Object> createTrendItem(String name, Long current, Long prev) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("current", current != null ? current : 0);
        item.put("previous", prev != null ? prev : 0);

        if (prev != null && prev > 0) {
            double change = ((double) (current - prev) / prev) * 100;
            item.put("changePercent", Math.round(change * 10) / 10.0);
            item.put("trend", change > 0 ? "UP" : (change < 0 ? "DOWN" : "STABLE"));
        } else if (current != null && current > 0) {
            item.put("changePercent", 100.0);
            item.put("trend", "NEW");
        } else {
            item.put("changePercent", 0.0);
            item.put("trend", "STABLE");
        }

        return item;
    }

    private String getRegionName(String regionCode) {
        Map<String, String> regionMap = new HashMap<>();
        regionMap.put("CD", "成都");
        regionMap.put("SH", "上海");
        regionMap.put("BJ", "北京");
        regionMap.put("GZ", "广州");
        regionMap.put("SZ", "深圳");
        regionMap.put("HK", "杭州");
        regionMap.put("NJ", "南京");
        regionMap.put("WH", "武汉");
        return regionMap.getOrDefault(regionCode, regionCode);
    }

    public List<Map<String, Object>> getAllRegions() {
        List<Map<String, Object>> regions = new ArrayList<>();

        String[] regionCodes = {"CD", "SH", "BJ", "GZ", "SZ", "HK", "NJ", "WH"};
        String[] regionNames = {"成都", "上海", "北京", "广州", "深圳", "杭州", "南京", "武汉"};

        for (int i = 0; i < regionCodes.length; i++) {
            Map<String, Object> region = new HashMap<>();
            region.put("code", regionCodes[i]);
            region.put("name", regionNames[i]);
            regions.add(region);
        }

        return regions;
    }
}
