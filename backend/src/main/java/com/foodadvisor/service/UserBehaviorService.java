package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.dto.response.BehaviorStatsDTO;
import com.foodadvisor.entity.UserBehaviorLog;
import com.foodadvisor.mapper.UserBehaviorLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserBehaviorService {

    private final UserBehaviorLogMapper behaviorLogMapper;

    @Transactional
    public void logSearch(Long userId, String keyword) {
        UserBehaviorLog logEntry = new UserBehaviorLog();
        logEntry.setEventId(UUID.randomUUID().toString());
        logEntry.setUserId(userId);
        logEntry.setEventType("SEARCH");
        logEntry.setSearchKeyword(keyword);
        logEntry.setCreatedAt(OffsetDateTime.now());
        saveIfNotExists(logEntry);
    }

    @Transactional
    public void logMerchantClick(Long userId, Long merchantId) {
        UserBehaviorLog logEntry = new UserBehaviorLog();
        logEntry.setEventId(UUID.randomUUID().toString());
        logEntry.setUserId(userId);
        logEntry.setEventType("MERCHANT_CLICK");
        logEntry.setMerchantId(merchantId);
        logEntry.setCreatedAt(OffsetDateTime.now());
        saveIfNotExists(logEntry);
    }

    @Transactional
    public void logSceneEntry(Long userId, String sceneType) {
        UserBehaviorLog logEntry = new UserBehaviorLog();
        logEntry.setEventId(UUID.randomUUID().toString());
        logEntry.setUserId(userId);
        logEntry.setEventType("SCENE_ENTRY");
        logEntry.setSceneType(sceneType);
        logEntry.setCreatedAt(OffsetDateTime.now());
        saveIfNotExists(logEntry);
    }

    @Transactional
    public void logTopicClick(Long userId, Long topicId) {
        UserBehaviorLog logEntry = new UserBehaviorLog();
        logEntry.setEventId(UUID.randomUUID().toString());
        logEntry.setUserId(userId);
        logEntry.setEventType("TOPIC_CLICK");
        logEntry.setTopicId(topicId);
        logEntry.setCreatedAt(OffsetDateTime.now());
        saveIfNotExists(logEntry);
    }

    @Transactional
    public void logTagClick(Long userId, String tagCode) {
        UserBehaviorLog logEntry = new UserBehaviorLog();
        logEntry.setEventId(UUID.randomUUID().toString());
        logEntry.setUserId(userId);
        logEntry.setEventType("TAG_CLICK");
        logEntry.setTagCode(tagCode);
        logEntry.setCreatedAt(OffsetDateTime.now());
        saveIfNotExists(logEntry);
    }

    @Transactional
    public void logFeedback(Long userId, String feedbackType, Integer feedbackScore) {
        UserBehaviorLog logEntry = new UserBehaviorLog();
        logEntry.setEventId(UUID.randomUUID().toString());
        logEntry.setUserId(userId);
        logEntry.setEventType("FEEDBACK");
        logEntry.setFeedbackType(feedbackType);
        logEntry.setFeedbackScore(feedbackScore);
        logEntry.setCreatedAt(OffsetDateTime.now());
        saveIfNotExists(logEntry);
    }

    private void saveIfNotExists(UserBehaviorLog logEntry) {
        try {
            behaviorLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("Duplicate event_id detected, skipping: {}", logEntry.getEventId());
        }
    }

    public BehaviorStatsDTO getStats(OffsetDateTime startTime, OffsetDateTime endTime) {
        Long totalEvents = behaviorLogMapper.getTotalEvents(startTime, endTime);
        Long activeUsers = behaviorLogMapper.getActiveUsers(startTime, endTime);
        List<Map<String, Object>> hotKeywords = behaviorLogMapper.getHotSearchKeywords(startTime, endTime, 20);
        List<Map<String, Object>> hotScenes = behaviorLogMapper.getHotScenes(startTime, endTime, 10);
        List<Map<String, Object>> hotMerchants = behaviorLogMapper.getHotMerchants(startTime, endTime, 15);
        List<Map<String, Object>> eventStats = behaviorLogMapper.getEventStats(startTime, endTime);
        List<Map<String, Object>> hotTags = behaviorLogMapper.getHotTags(startTime, endTime, 15);
        List<Map<String, Object>> dailyStats = behaviorLogMapper.getDailyStats(startTime, endTime);

        return BehaviorStatsDTO.builder()
                .totalEvents(totalEvents)
                .activeUsers(activeUsers)
                .hotKeywords(hotKeywords)
                .hotScenes(hotScenes)
                .hotMerchants(hotMerchants)
                .eventStats(eventStats)
                .hotTags(hotTags)
                .dailyStats(dailyStats)
                .build();
    }

    public List<UserBehaviorLog> getEventLogs(OffsetDateTime startTime, OffsetDateTime endTime, String eventType) {
        LambdaQueryWrapper<UserBehaviorLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(UserBehaviorLog::getCreatedAt, startTime, endTime);
        if (eventType != null && !eventType.isEmpty()) {
            wrapper.eq(UserBehaviorLog::getEventType, eventType);
        }
        wrapper.orderByDesc(UserBehaviorLog::getCreatedAt);
        return behaviorLogMapper.selectList(wrapper);
    }
}
