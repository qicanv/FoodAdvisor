package com.foodadvisor.service;

import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.entity.ModerationKeyword;
import com.foodadvisor.entity.ModerationRule;
import com.foodadvisor.entity.ReviewRuleMatch;
import com.foodadvisor.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final ModerationRuleMapper moderationRuleMapper;
    private final ModerationKeywordMapper moderationKeywordMapper;
    private final ReviewRuleMatchMapper reviewRuleMatchMapper;

    private final Map<String, List<String>> keywordCache = new ConcurrentHashMap<>();
    private final Map<String, ModerationRule> ruleCache = new ConcurrentHashMap<>();

    public Map<String, Object> getReviewList(String riskLevel, String moderationStatus,
                                             Long merchantId, OffsetDateTime startTime,
                                             OffsetDateTime endTime, Integer pageNum, Integer pageSize) {
        int offset = (pageNum - 1) * pageSize;

        List<Map<String, Object>> records = reviewMapper.getModerationList(
                riskLevel, moderationStatus, merchantId, startTime, endTime, pageSize, offset);

        Long total = reviewMapper.countModerationList(
                riskLevel, moderationStatus, merchantId, startTime, endTime);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        result.put("totalPages", (int) Math.ceil((double) total / pageSize));

        return result;
    }

    public Map<String, Object> getReviewDetail(Long id) {
        Map<String, Object> detail = reviewMapper.getReviewDetailWithRelations(id);
        if (detail != null) {
            List<Map<String, Object>> ruleMatches = reviewRuleMatchMapper.findByReviewIdWithRuleInfo(id);
            detail.put("ruleMatches", ruleMatches);

            List<Map<String, Object>> matchedRules = new ArrayList<>();
            for (Map<String, Object> match : ruleMatches) {
                Map<String, Object> ruleInfo = new HashMap<>();
                ruleInfo.put("ruleCode", match.get("rule_code"));
                ruleInfo.put("ruleName", match.get("rule_name"));
                ruleInfo.put("description", match.get("description"));
                ruleInfo.put("riskLevel", match.get("risk_level"));
                ruleInfo.put("keyword", match.get("keyword"));
                matchedRules.add(ruleInfo);
            }
            detail.put("matchedRules", matchedRules);
        }
        return detail;
    }

    public List<Map<String, Object>> getActiveMerchants() {
        return reviewMapper.getActiveMerchants();
    }

    public Long countPendingReviews() {
        return reviewMapper.countPendingReviews();
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pending", reviewMapper.countPendingReviews());
        stats.put("highRisk", reviewMapper.countHighRiskReviews());
        stats.put("mediumRisk", reviewMapper.countMediumRiskReviews());
        return stats;
    }

    @Transactional
    public Map<String, Object> moderateReview(Long reviewId, String action, String remark,
                                              Long operatorUserId, String operatorUsername, String operatorRole) {
        Map<String, Object> result = new HashMap<>();

        String currentStatus = reviewMapper.getCurrentModerationStatus(reviewId);
        if (currentStatus == null) {
            result.put("success", false);
            result.put("message", "评价不存在或已删除");
            return result;
        }

        String newModerationStatus;
        String newStatus;
        String newOperator;

        switch (action.toUpperCase()) {
            case "APPROVE":
                newModerationStatus = "APPROVED";
                newStatus = "PUBLISHED";
                newOperator = operatorUsername;
                break;
            case "REJECT":
                newModerationStatus = "REJECTED";
                newStatus = "HIDDEN";
                newOperator = operatorUsername;
                break;
            case "DELETE":
                newModerationStatus = "REJECTED";
                newStatus = "DELETED";
                newOperator = operatorUsername;
                break;
            case "RETURN_FOR_MODIFICATION":
                newModerationStatus = "PENDING";
                newStatus = "PENDING";
                newOperator = operatorUsername;
                break;
            case "UNDO_APPROVE":
                newModerationStatus = "PENDING";
                newStatus = "PENDING";
                newOperator = null;
                break;
            case "UNDO_REJECT":
                newModerationStatus = "PENDING";
                newStatus = "PENDING";
                newOperator = null;
                break;
            default:
                result.put("success", false);
                result.put("message", "无效的审核操作");
                return result;
        }

        int updated = reviewMapper.updateModerationStatus(reviewId, newModerationStatus, newStatus, newOperator);

        if (updated > 0) {
            recordAuditLog(reviewId, action, currentStatus, newModerationStatus, remark,
                    operatorUserId, operatorUsername, operatorRole);

            result.put("success", true);
            result.put("message", getActionSuccessMessage(action));
            result.put("newModerationStatus", newModerationStatus);
            result.put("newStatus", newStatus);
        } else {
            result.put("success", false);
            result.put("message", "审核操作失败");
        }

        return result;
    }

    @Transactional
    public void refreshRiskDetection(Long reviewId) {
        Map<String, Object> reviewDetail = reviewMapper.getReviewDetailWithRelations(reviewId);
        if (reviewDetail == null) {
            return;
        }

        String content = (String) reviewDetail.get("content");
        if (content == null || content.isEmpty()) {
            return;
        }

        reviewRuleMatchMapper.deleteByReviewId(reviewId);

        List<ModerationRule> rules = moderationRuleMapper.findAllEnabled();
        String highestRiskLevel = "LOW";
        List<ReviewRuleMatch> matches = new ArrayList<>();

        for (ModerationRule rule : rules) {
            List<String> keywords = getKeywordsForRule(rule.getRuleCode());
            for (String keyword : keywords) {
                if (content.contains(keyword)) {
                    ReviewRuleMatch match = new ReviewRuleMatch();
                    match.setReviewId(reviewId);
                    match.setRuleCode(rule.getRuleCode());
                    match.setKeyword(keyword);
                    match.setMatchPosition(content.indexOf(keyword));
                    matches.add(match);

                    if (isHigherRisk(rule.getRiskLevel(), highestRiskLevel)) {
                        highestRiskLevel = rule.getRiskLevel();
                    }
                }
            }
        }

        for (ReviewRuleMatch match : matches) {
            reviewRuleMatchMapper.insert(match);
        }

        reviewMapper.updateModerationStatus(reviewId, "PENDING", "PENDING", null);
        reviewMapper.updateRiskLevel(reviewId, highestRiskLevel);
    }

    @Transactional
    public void refreshAllRiskDetection() {
        List<Map<String, Object>> pendingReviews = reviewMapper.getModerationList(
                null, "PENDING", null, null, null, Integer.MAX_VALUE, 0);

        for (Map<String, Object> review : pendingReviews) {
            Long reviewId = ((Number) review.get("id")).longValue();
            try {
                refreshRiskDetection(reviewId);
            } catch (Exception e) {
                log.error("Failed to refresh risk detection for review {}", reviewId, e);
            }
        }
    }

    private List<String> getKeywordsForRule(String ruleCode) {
        return keywordCache.computeIfAbsent(ruleCode, k -> {
            List<ModerationKeyword> keywords = moderationKeywordMapper.findByRuleCode(k);
            return keywords.stream().map(ModerationKeyword::getKeyword).toList();
        });
    }

    private boolean isHigherRisk(String risk1, String risk2) {
        Map<String, Integer> riskOrder = Map.of("LOW", 1, "MEDIUM", 2, "HIGH", 3);
        return riskOrder.getOrDefault(risk1, 0) > riskOrder.getOrDefault(risk2, 0);
    }

    private void recordAuditLog(Long reviewId, String action, String previousStatus,
                                String newStatus, String remark, Long operatorUserId,
                                String operatorUsername, String operatorRole) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperationType("CONTENT_MODERATION");
        auditLog.setOperatorUserId(operatorUserId);
        auditLog.setOperatorUsername(operatorUsername);
        auditLog.setOperatorRole(operatorRole);
        auditLog.setModule("CONTENT_MODERATION");
        auditLog.setLevel("INFO");
        auditLog.setResult("SUCCESS");
        auditLog.setObjectType("REVIEW");
        auditLog.setObjectId(String.valueOf(reviewId));

        auditLog.setMetadata("{\"action\":\"" + action + "\",\"previousStatus\":\"" + previousStatus + "\",\"newStatus\":\"" + newStatus + "\",\"remark\":\"" + (remark != null ? remark.replace("\"", "\\\"") : "") + "\"}");

        auditLogService.recordSafely(auditLog);
    }

    private String getModerationStatusText(String status) {
        Map<String, String> map = new HashMap<>();
        map.put("PENDING", "待审核");
        map.put("APPROVED", "已通过");
        map.put("REJECTED", "已驳回");
        return map.getOrDefault(status, status);
    }

    private String getActionSuccessMessage(String action) {
        Map<String, String> map = new HashMap<>();
        map.put("APPROVE", "审核通过");
        map.put("REJECT", "审核驳回");
        map.put("DELETE", "已删除");
        map.put("RETURN_FOR_MODIFICATION", "已退回修改");
        map.put("UNDO_APPROVE", "已撤销通过");
        map.put("UNDO_REJECT", "已撤销驳回");
        return map.getOrDefault(action, "操作成功");
    }
}