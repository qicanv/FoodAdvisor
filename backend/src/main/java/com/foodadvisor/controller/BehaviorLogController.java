package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.service.UserBehaviorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/behavior/log")
@RequiredArgsConstructor
public class BehaviorLogController {

    private final UserBehaviorService userBehaviorService;

    @PostMapping("/search")
    public ApiResponse<Void> logSearch(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        String keyword = (String) request.get("keyword");

        userBehaviorService.logSearch(userId, keyword);

        return ApiResponse.success("搜索日志已记录", null);
    }

    @PostMapping("/merchant-click")
    public ApiResponse<Void> logMerchantClick(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        Long merchantId = ((Number) request.get("merchantId")).longValue();

        userBehaviorService.logMerchantClick(userId, merchantId);

        return ApiResponse.success("商家点击日志已记录", null);
    }

    @PostMapping("/scene-entry")
    public ApiResponse<Void> logSceneEntry(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        String sceneType = (String) request.get("sceneType");

        userBehaviorService.logSceneEntry(userId, sceneType);

        return ApiResponse.success("场景入口日志已记录", null);
    }

    @PostMapping("/topic-click")
    public ApiResponse<Void> logTopicClick(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        Long topicId = ((Number) request.get("topicId")).longValue();

        userBehaviorService.logTopicClick(userId, topicId);

        return ApiResponse.success("专题点击日志已记录", null);
    }

    @PostMapping("/tag-click")
    public ApiResponse<Void> logTagClick(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        String tagCode = (String) request.get("tagCode");

        userBehaviorService.logTagClick(userId, tagCode);

        return ApiResponse.success("标签点击日志已记录", null);
    }

    @PostMapping("/feedback")
    public ApiResponse<Void> logFeedback(
            @RequestBody Map<String, Object> request) {

        Long userId = request.containsKey("userId") ? ((Number) request.get("userId")).longValue() : null;
        String feedbackType = (String) request.get("feedbackType");
        Integer feedbackScore = request.containsKey("feedbackScore") ? ((Number) request.get("feedbackScore")).intValue() : null;

        userBehaviorService.logFeedback(userId, feedbackType, feedbackScore);

        return ApiResponse.success("反馈日志已记录", null);
    }
}
