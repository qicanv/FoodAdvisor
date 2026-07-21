package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.prompt.CreatePromptVersionRequest;
import com.foodadvisor.dto.prompt.PromptActivationLogResponse;
import com.foodadvisor.dto.prompt.PromptDefinitionResponse;
import com.foodadvisor.dto.prompt.PromptVersionResponse;
import com.foodadvisor.dto.prompt.PromptVersionSwitchRequest;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.AuditLogService;
import com.foodadvisor.service.PromptManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/prompts")
public class PromptManagementController {

    private static final Logger log =
            LoggerFactory.getLogger(PromptManagementController.class);

    private final PromptManagementService promptManagementService;
    private final AdminAccessGuard adminAccessGuard;
    private final AuditLogService auditLogService;

    public PromptManagementController(
            PromptManagementService promptManagementService,
            AdminAccessGuard adminAccessGuard,
            AuditLogService auditLogService
    ) {
        this.promptManagementService = promptManagementService;
        this.adminAccessGuard = adminAccessGuard;
        this.auditLogService = auditLogService;
    }

    /**
     * 查询全部提示词场景及当前启用版本。
     */
    @GetMapping
    public ApiResponse<List<PromptDefinitionResponse>> listDefinitions(
            @RequestHeader(
                    value = "X-User-Role",
                    required = false
            ) String role,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);
        return ApiResponse.success(
                promptManagementService.listDefinitions()
        );
    }

    /**
     * 查询单个提示词场景。
     */
    @GetMapping("/{sceneCode}")
    public ApiResponse<PromptDefinitionResponse> getDefinition(
            @RequestHeader(
                    value = "X-User-Role",
                    required = false
            ) String role,
            @PathVariable String sceneCode,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);
        return ApiResponse.success(
                promptManagementService.getDefinition(sceneCode)
        );
    }

    /**
     * 查询指定场景的全部历史版本。
     */
    @GetMapping("/{sceneCode}/versions")
    public ApiResponse<List<PromptVersionResponse>> listVersions(
            @RequestHeader(
                    value = "X-User-Role",
                    required = false
            ) String role,
            @PathVariable String sceneCode,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);
        return ApiResponse.success(
                promptManagementService.listVersions(sceneCode)
        );
    }

    /**
     * 创建新版本。旧版本不会被修改。
     */
    @PostMapping("/{sceneCode}/versions")
    public ApiResponse<PromptVersionResponse> createVersion(
            @RequestHeader(
                    value = "X-User-Role",
                    required = false
            ) String role,
            @PathVariable String sceneCode,
            @Valid @RequestBody CreatePromptVersionRequest request,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);

        Long operatorUserId = toLong(
                servletRequest.getAttribute("userId")
        );

        PromptVersionResponse response =
                promptManagementService.createVersion(
                        sceneCode,
                        request,
                        operatorUserId
                );

        recordPromptOperation(
                servletRequest,
                "CREATE_PROMPT_VERSION",
                "PROMPT_VERSION",
                response.id(),
                response.sceneCode(),
                response.versionNo(),
                response.versionTag(),
                request.activate(),
                request.content() == null
                        ? 0
                        : request.content().length()
        );

        return ApiResponse.success(response);
    }

    /**
     * 启用指定提示词版本。
     */
    @PostMapping("/{sceneCode}/versions/{versionId}/activate")
    public ApiResponse<PromptVersionResponse> activateVersion(
            @RequestHeader(
                    value = "X-User-Role",
                    required = false
            ) String role,
            @PathVariable String sceneCode,
            @PathVariable Long versionId,
            @Valid
            @RequestBody(required = false)
            PromptVersionSwitchRequest request,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);

        Long operatorUserId = toLong(
                servletRequest.getAttribute("userId")
        );

        PromptVersionResponse response =
                promptManagementService.activateVersion(
                        sceneCode,
                        versionId,
                        request,
                        operatorUserId
                );

        recordPromptOperation(
                servletRequest,
                "ACTIVATE_PROMPT_VERSION",
                "PROMPT_VERSION",
                response.id(),
                response.sceneCode(),
                response.versionNo(),
                response.versionTag(),
                true,
                response.content() == null
                        ? 0
                        : response.content().length()
        );

        return ApiResponse.success(response);
    }

    /**
     * 回滚到指定历史版本。
     */
    @PostMapping("/{sceneCode}/versions/{versionId}/rollback")
    public ApiResponse<PromptVersionResponse> rollbackVersion(
            @RequestHeader(
                    value = "X-User-Role",
                    required = false
            ) String role,
            @PathVariable String sceneCode,
            @PathVariable Long versionId,
            @Valid
            @RequestBody(required = false)
            PromptVersionSwitchRequest request,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);

        Long operatorUserId = toLong(
                servletRequest.getAttribute("userId")
        );

        PromptVersionResponse response =
                promptManagementService.rollbackVersion(
                        sceneCode,
                        versionId,
                        request,
                        operatorUserId
                );

        recordPromptOperation(
                servletRequest,
                "ROLLBACK_PROMPT_VERSION",
                "PROMPT_VERSION",
                response.id(),
                response.sceneCode(),
                response.versionNo(),
                response.versionTag(),
                true,
                response.content() == null
                        ? 0
                        : response.content().length()
        );

        return ApiResponse.success(response);
    }

    /**
     * 查询启用和回滚操作历史。
     */
    @GetMapping("/{sceneCode}/activation-logs")
    public ApiResponse<List<PromptActivationLogResponse>>
    listActivationLogs(
            @RequestHeader(
                    value = "X-User-Role",
                    required = false
            ) String role,
            @PathVariable String sceneCode,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);
        return ApiResponse.success(
                promptManagementService.listActivationLogs(sceneCode)
        );
    }

    private void requireAdmin(
            HttpServletRequest request,
            String headerRole
    ) {
        Object jwtRole = request.getAttribute("role");

        if (jwtRole != null) {
            adminAccessGuard.requireAdmin(jwtRole.toString());
            return;
        }

        adminAccessGuard.requireAdmin(headerRole);
    }

    private void recordPromptOperation(
            HttpServletRequest request,
            String operation,
            String objectType,
            Long objectId,
            String sceneCode,
            Integer versionNo,
            String versionTag,
            boolean active,
            int contentLength
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperationType("ADMIN_OPERATION");
        auditLog.setModule("PROMPT_MANAGEMENT");
        auditLog.setLevel("INFO");
        auditLog.setResult("SUCCESS");
        auditLog.setOperatorUserId(
                toLong(request.getAttribute("userId"))
        );
        auditLog.setOperatorUsername(
                toStringValue(request.getAttribute("username"))
        );
        auditLog.setOperatorRole(
                toStringValue(request.getAttribute("role"))
        );
        auditLog.setObjectType(objectType);
        auditLog.setObjectId(
                objectId == null ? null : String.valueOf(objectId)
        );
        auditLog.setRequestMethod(request.getMethod());
        auditLog.setRequestUri(request.getRequestURI());
        auditLog.setIpAddress(clientIp(request));
        auditLog.setUserAgent(request.getHeader("User-Agent"));
        auditLog.setMetadata(
                createMetadata(
                        operation,
                        sceneCode,
                        versionNo,
                        versionTag,
                        active,
                        contentLength
                )
        );

        try {
            auditLogService.recordSafely(auditLog);
        } catch (Exception exception) {
            log.warn(
                    "Prompt management audit logging failed: {}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private String createMetadata(
            String operation,
            String sceneCode,
            Integer versionNo,
            String versionTag,
            boolean active,
            int contentLength
    ) {
        return "{\"operation\":\""
                + escape(operation)
                + "\",\"sceneCode\":\""
                + escape(sceneCode)
                + "\",\"versionNo\":"
                + (versionNo == null ? "null" : versionNo)
                + ",\"versionTag\":\""
                + escape(versionTag)
                + "\",\"active\":"
                + active
                + ",\"contentLength\":"
                + contentLength
                + "}";
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor =
                request.getHeader("X-Forwarded-For");

        if (forwardedFor != null
                && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private Long toLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value == null) {
            return null;
        }

        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}