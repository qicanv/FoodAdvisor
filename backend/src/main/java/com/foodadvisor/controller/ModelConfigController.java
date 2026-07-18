package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.modelconfig.ConnectionTestResponse;
import com.foodadvisor.dto.modelconfig.ModelConfigRequest;
import com.foodadvisor.dto.modelconfig.ModelConfigResponse;
import com.foodadvisor.dto.modelconfig.SceneBindingRequest;
import com.foodadvisor.dto.modelconfig.SceneBindingResponse;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.ModelConfigService;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/model-configs")
public class ModelConfigController {

    private static final Logger log =
            LoggerFactory.getLogger(ModelConfigController.class);

    private final ModelConfigService modelConfigService;
    private final AdminAccessGuard adminAccessGuard;
    private final AuditLogService auditLogService;

    public ModelConfigController(
            ModelConfigService modelConfigService,
            AdminAccessGuard adminAccessGuard,
            AuditLogService auditLogService
    ) {
        this.modelConfigService = modelConfigService;
        this.adminAccessGuard = adminAccessGuard;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<List<ModelConfigResponse>> listConfigs(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);
        return ApiResponse.success(modelConfigService.listConfigs());
    }

    @PostMapping
    public ApiResponse<ModelConfigResponse> createConfig(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody ModelConfigRequest request,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);
        ModelConfigResponse response =
                modelConfigService.createConfig(request);
        recordModelConfigOperation(
                servletRequest,
                "CREATE_MODEL_CONFIG",
                "MODEL_CONFIG",
                response.id(),
                "INFO",
                "SUCCESS",
                createMetadata(request, true)
        );
        return ApiResponse.success(response);
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelConfigResponse> updateConfig(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id,
            @Valid @RequestBody ModelConfigRequest request,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);
        ModelConfigResponse response =
                modelConfigService.updateConfig(id, request);
        recordModelConfigOperation(
                servletRequest,
                "UPDATE_MODEL_CONFIG",
                "MODEL_CONFIG",
                id,
                "INFO",
                "SUCCESS",
                updateMetadata(request)
        );
        return ApiResponse.success(response);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<ConnectionTestResponse> testConfig(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);
        ConnectionTestResponse response =
                modelConfigService.testConfig(id);
        recordModelConfigOperation(
                servletRequest,
                "TEST_MODEL_CONFIG_CONNECTION",
                "MODEL_CONFIG",
                id,
                response.success() ? "INFO" : "WARN",
                response.success() ? "SUCCESS" : "FAILURE",
                "{\"operation\":\"TEST_MODEL_CONFIG_CONNECTION\","
                        + "\"testSuccess\":" + response.success()
                        + ",\"statusCode\":"
                        + (response.httpStatus() == null
                        ? "null"
                        : response.httpStatus())
                        + "}"
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/scene-bindings")
    public ApiResponse<List<SceneBindingResponse>> listSceneBindings(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);
        return ApiResponse.success(modelConfigService.listSceneBindings());
    }

    @PutMapping("/scene-bindings")
    public ApiResponse<SceneBindingResponse> bindScene(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody SceneBindingRequest request,
            HttpServletRequest servletRequest
    ) {
        requireAdmin(servletRequest, role);
        SceneBindingResponse response =
                modelConfigService.bindScene(request);
        recordModelConfigOperation(
                servletRequest,
                "BIND_MODEL_SCENE",
                "MODEL_SCENE_BINDING",
                response.id(),
                "INFO",
                "SUCCESS",
                "{\"operation\":\"BIND_MODEL_SCENE\","
                        + "\"sceneType\":\"" + escape(request.sceneType())
                        + "\",\"modelConfigId\":"
                        + request.modelConfigId()
                        + "}"
        );
        return ApiResponse.success(response);
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

    private void recordModelConfigOperation(
            HttpServletRequest request,
            String operation,
            String objectType,
            Long objectId,
            String level,
            String result,
            String metadata
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperationType("ADMIN_OPERATION");
        auditLog.setModule("MODEL_CONFIG");
        auditLog.setLevel(level);
        auditLog.setResult(result);
        auditLog.setOperatorUserId(toLong(request.getAttribute("userId")));
        auditLog.setOperatorUsername(toStringValue(
                request.getAttribute("username")
        ));
        auditLog.setOperatorRole(toStringValue(request.getAttribute("role")));
        auditLog.setObjectType(objectType);
        auditLog.setObjectId(objectId == null ? null : String.valueOf(objectId));
        auditLog.setRequestMethod(request.getMethod());
        auditLog.setRequestUri(request.getRequestURI());
        auditLog.setIpAddress(clientIp(request));
        auditLog.setUserAgent(request.getHeader("User-Agent"));
        auditLog.setMetadata(metadata);
        auditLog.setErrorCode("FAILURE".equals(result) ? operation : null);
        auditLog.setErrorMessage(
                "FAILURE".equals(result)
                        ? "Administrator operation failed"
                        : null
        );
        try {
            auditLogService.recordSafely(auditLog);
        } catch (Exception exception) {
            log.warn(
                    "Model config audit logging failed: {}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private String createMetadata(
            ModelConfigRequest request,
            boolean creating
    ) {
        return "{\"operation\":\"CREATE_MODEL_CONFIG\","
                + "\"provider\":\"" + escape(request.provider()) + "\","
                + "\"modelName\":\"" + escape(request.modelName()) + "\","
                + "\"status\":\"" + escape(request.status()) + "\","
                + "\"apiKeyConfigured\":" + hasApiKey(request)
                + ",\"apiKeyChanged\":" + creating
                + "}";
    }

    private String updateMetadata(ModelConfigRequest request) {
        return "{\"operation\":\"UPDATE_MODEL_CONFIG\","
                + "\"provider\":\"" + escape(request.provider()) + "\","
                + "\"modelName\":\"" + escape(request.modelName()) + "\","
                + "\"status\":\"" + escape(request.status()) + "\","
                + "\"changedFields\":["
                + "\"configName\",\"provider\",\"modelName\",\"baseUrl\","
                + "\"timeoutMs\",\"temperature\",\"maxOutputTokens\",\"status\""
                + "],\"apiKeyChanged\":" + hasApiKey(request)
                + "}";
    }

    private boolean hasApiKey(ModelConfigRequest request) {
        return request.apiKey() != null && !request.apiKey().isBlank();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
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
