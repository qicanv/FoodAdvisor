package com.foodadvisor.service;

import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.entity.User;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

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
        return reviewMapper.getReviewDetailWithRelations(id);
    }

    public List<Map<String, Object>> getActiveMerchants() {
        return reviewMapper.getActiveMerchants();
    }

    public Long countPendingReviews() {
        return reviewMapper.countPendingReviews();
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

        if (!"PENDING".equals(currentStatus)) {
            result.put("success", false);
            result.put("message", "状态冲突：该评价已处理，当前状态为 " + getModerationStatusText(currentStatus));
            return result;
        }

        String newModerationStatus;
        String newStatus;

        switch (action.toUpperCase()) {
            case "APPROVE":
                newModerationStatus = "APPROVED";
                newStatus = "PUBLISHED";
                break;
            case "REJECT":
                newModerationStatus = "REJECTED";
                newStatus = "HIDDEN";
                break;
            case "DELETE":
                newModerationStatus = "REJECTED";
                newStatus = "DELETED";
                break;
            case "RETURN_FOR_MODIFICATION":
                newModerationStatus = "PENDING";
                newStatus = "PENDING";
                break;
            default:
                result.put("success", false);
                result.put("message", "无效的审核操作");
                return result;
        }

        int updated = reviewMapper.updateModerationStatus(reviewId, newModerationStatus, newStatus);

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

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", action);
        metadata.put("previousStatus", previousStatus);
        metadata.put("newStatus", newStatus);
        metadata.put("remark", remark);
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
        return map.getOrDefault(action, "操作成功");
    }
}