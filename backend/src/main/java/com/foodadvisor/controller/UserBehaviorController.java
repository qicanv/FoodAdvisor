package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.response.BehaviorStatsDTO;
import com.foodadvisor.entity.UserBehaviorLog;
import com.foodadvisor.service.UserBehaviorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/behavior")
@RequiredArgsConstructor
public class UserBehaviorController {

    private final UserBehaviorService userBehaviorService;

    @GetMapping("/stats")
    public ApiResponse<BehaviorStatsDTO> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        OffsetDateTime start = startTime.atOffset(ZoneOffset.ofHours(8));
        OffsetDateTime end = endTime.atOffset(ZoneOffset.ofHours(8));

        BehaviorStatsDTO stats = userBehaviorService.getStats(start, end);

        return ApiResponse.success(stats);
    }

    @GetMapping("/logs")
    public ApiResponse<List<UserBehaviorLog>> getLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String eventType) {

        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        OffsetDateTime start = startTime.atOffset(ZoneOffset.ofHours(8));
        OffsetDateTime end = endTime.atOffset(ZoneOffset.ofHours(8));

        List<UserBehaviorLog> logs = userBehaviorService.getEventLogs(start, end, eventType);

        return ApiResponse.success(logs);
    }

    @PostMapping("/log/search")
    public ApiResponse<Void> logSearch(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        String keyword = (String) request.get("keyword");

        userBehaviorService.logSearch(userId, keyword);

        return ApiResponse.success("搜索日志已记录", null);
    }

    @PostMapping("/log/merchant-click")
    public ApiResponse<Void> logMerchantClick(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        Long merchantId = ((Number) request.get("merchantId")).longValue();

        userBehaviorService.logMerchantClick(userId, merchantId);

        return ApiResponse.success("商家点击日志已记录", null);
    }

    @PostMapping("/log/scene-entry")
    public ApiResponse<Void> logSceneEntry(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        String sceneType = (String) request.get("sceneType");

        userBehaviorService.logSceneEntry(userId, sceneType);

        return ApiResponse.success("场景入口日志已记录", null);
    }

    @PostMapping("/log/topic-click")
    public ApiResponse<Void> logTopicClick(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        Long topicId = ((Number) request.get("topicId")).longValue();

        userBehaviorService.logTopicClick(userId, topicId);

        return ApiResponse.success("专题点击日志已记录", null);
    }

    @PostMapping("/log/tag-click")
    public ApiResponse<Void> logTagClick(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        String tagCode = (String) request.get("tagCode");

        userBehaviorService.logTagClick(userId, tagCode);

        return ApiResponse.success("标签点击日志已记录", null);
    }

    @PostMapping("/log/feedback")
    public ApiResponse<Void> logFeedback(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        String feedbackType = (String) request.get("feedbackType");
        Integer feedbackScore = request.containsKey("feedbackScore") ? ((Number) request.get("feedbackScore")).intValue() : null;

        userBehaviorService.logFeedback(userId, feedbackType, feedbackScore);

        return ApiResponse.success("反馈日志已记录", null);
    }
}
