package com.foodadvisor.backend.controller;

import com.foodadvisor.backend.common.ApiResponse;
import com.foodadvisor.backend.dto.modelconfig.ConnectionTestResponse;
import com.foodadvisor.backend.dto.modelconfig.ModelConfigRequest;
import com.foodadvisor.backend.dto.modelconfig.ModelConfigResponse;
import com.foodadvisor.backend.dto.modelconfig.SceneBindingRequest;
import com.foodadvisor.backend.dto.modelconfig.SceneBindingResponse;
import com.foodadvisor.backend.security.AdminAccessGuard;
import com.foodadvisor.backend.service.ModelConfigService;
import jakarta.validation.Valid;
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

    private final ModelConfigService modelConfigService;
    private final AdminAccessGuard adminAccessGuard;

    public ModelConfigController(
            ModelConfigService modelConfigService,
            AdminAccessGuard adminAccessGuard
    ) {
        this.modelConfigService = modelConfigService;
        this.adminAccessGuard = adminAccessGuard;
    }

    @GetMapping
    public ApiResponse<List<ModelConfigResponse>> listConfigs(
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        adminAccessGuard.requireAdmin(role);
        return ApiResponse.success(modelConfigService.listConfigs());
    }

    @PostMapping
    public ApiResponse<ModelConfigResponse> createConfig(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody ModelConfigRequest request
    ) {
        adminAccessGuard.requireAdmin(role);
        return ApiResponse.success(modelConfigService.createConfig(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelConfigResponse> updateConfig(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id,
            @Valid @RequestBody ModelConfigRequest request
    ) {
        adminAccessGuard.requireAdmin(role);
        return ApiResponse.success(modelConfigService.updateConfig(id, request));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<ConnectionTestResponse> testConfig(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable Long id
    ) {
        adminAccessGuard.requireAdmin(role);
        return ApiResponse.success(modelConfigService.testConfig(id));
    }

    @GetMapping("/scene-bindings")
    public ApiResponse<List<SceneBindingResponse>> listSceneBindings(
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        adminAccessGuard.requireAdmin(role);
        return ApiResponse.success(modelConfigService.listSceneBindings());
    }

    @PutMapping("/scene-bindings")
    public ApiResponse<SceneBindingResponse> bindScene(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody SceneBindingRequest request
    ) {
        adminAccessGuard.requireAdmin(role);
        return ApiResponse.success(modelConfigService.bindScene(request));
    }
}
