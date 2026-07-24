package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.alert.DetectSensitiveRequest;
import com.foodadvisor.dto.alert.SensitiveAlertDTO;
import com.foodadvisor.dto.alert.SensitiveAlertDetailDTO;
import com.foodadvisor.dto.alert.UpdateAlertStatusRequest;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.service.SensitiveAlertService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 敏感话题预警接口
 * EPIC-03 故事6：敏感话题预警
 *
 * 权限：ADMIN 或 OPERATOR
 */
@RestController
@RequestMapping("/api/admin/sensitive-alerts")
public class SensitiveAlertController {

    private final SensitiveAlertService sensitiveAlertService;

    public SensitiveAlertController(SensitiveAlertService sensitiveAlertService) {
        this.sensitiveAlertService = sensitiveAlertService;
    }

    /**
     * 查询预警列表
     * 支持按状态、话题类型、风险等级、商家ID、时间范围筛选和分页
     */
    @GetMapping
    public ApiResponse<PageResult<SensitiveAlertDTO>> listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String topicType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {

        requireAdminOrOperator(request);

        PageResult<SensitiveAlertDTO> result = sensitiveAlertService.listAlerts(
                status, topicType, riskLevel, merchantId,
                startTime, endTime, pageNum, pageSize);

        return ApiResponse.success(result);
    }

    /**
     * 获取预警详情（包含关联的原始评价）
     */
    @GetMapping("/{id}")
    public ApiResponse<SensitiveAlertDetailDTO> getAlertDetail(
            @PathVariable Long id,
            HttpServletRequest request) {

        requireAdminOrOperator(request);

        SensitiveAlertDetailDTO detail = sensitiveAlertService.getAlertDetail(id);
        return ApiResponse.success(detail);
    }

    /**
     * 更新预警处理状态
     */
    @PutMapping("/{id}/status")
    public ApiResponse<SensitiveAlertDTO> updateAlertStatus(
            @PathVariable Long id,
            @RequestBody UpdateAlertStatusRequest updateRequest,
            HttpServletRequest request) {

        requireAdminOrOperator(request);

        Long operatorUserId = getOperatorUserId(request);
        String operatorUsername = getOperatorUsername(request);

        SensitiveAlertDTO result = sensitiveAlertService.updateAlertStatus(
                id, updateRequest, operatorUserId, operatorUsername);

        return ApiResponse.success(result);
    }

    /**
     * 手动触发敏感话题检测
     * 分析指定时间范围内的评价并生成预警
     */
    @PostMapping("/detect")
    public ApiResponse<List<SensitiveAlertDTO>> detectSensitiveTopics(
            @RequestBody(required = false) DetectSensitiveRequest detectRequest,
            HttpServletRequest request) {

        requireAdminOrOperator(request);

        if (detectRequest == null) {
            detectRequest = new DetectSensitiveRequest();
        }

        List<SensitiveAlertDTO> alerts = sensitiveAlertService.detectSensitiveTopics(detectRequest);
        return ApiResponse.success("检测完成，共生成/更新 " + alerts.size() + " 条预警", alerts);
    }

    /**
     * 获取待处理预警数量
     */
    @GetMapping("/pending-count")
    public ApiResponse<Long> getPendingCount(HttpServletRequest request) {
        requireAdminOrOperator(request);
        return ApiResponse.success(sensitiveAlertService.countPendingAlerts());
    }

    /**
     * 获取所有话题类型枚举
     */
    @GetMapping("/topic-types")
    public ApiResponse<List<java.util.Map<String, String>>> getTopicTypes(HttpServletRequest request) {
        requireAdminOrOperator(request);

        List<java.util.Map<String, String>> types = List.of(
                java.util.Map.of("value", "FOOD_SAFETY", "label", "食品安全"),
                java.util.Map.of("value", "HYGIENE", "label", "卫生问题"),
                java.util.Map.of("value", "CONCENTRATED_COMPLAINT", "label", "集中投诉"),
                java.util.Map.of("value", "SERVICE_DISPUTE", "label", "严重服务纠纷")
        );

        return ApiResponse.success(types);
    }

    // ============================================================
    // 权限工具方法
    // ============================================================

    private void requireAdminOrOperator(HttpServletRequest request) {
        String role = getOperatorRole(request);
        if (!"ADMIN".equalsIgnoreCase(role) && !"OPERATOR".equalsIgnoreCase(role)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "仅平台管理员和运营人员可访问敏感话题预警功能"
            );
        }
    }

    private Long getOperatorUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String getOperatorUsername(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        return username != null ? username.toString() : null;
    }

    private String getOperatorRole(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        return role != null ? role.toString() : null;
    }
}
