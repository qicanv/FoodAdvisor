package com.foodadvisor.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.backend.dto.modelconfig.ConnectionTestResponse;
import com.foodadvisor.backend.dto.modelconfig.ModelConfigRequest;
import com.foodadvisor.backend.dto.modelconfig.ModelConfigResponse;
import com.foodadvisor.backend.dto.modelconfig.SceneBindingRequest;
import com.foodadvisor.backend.dto.modelconfig.SceneBindingResponse;
import com.foodadvisor.backend.exception.GlobalExceptionHandler;
import com.foodadvisor.backend.security.AdminAccessGuard;
import com.foodadvisor.backend.service.ModelConfigService;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.service.AuditLogService;
import com.foodadvisor.util.SensitiveLogSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ModelConfigControllerTest {

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ModelConfigController(
                        modelConfigService,
                        new AdminAccessGuard(),
                        auditLogService
                ))
                .setControllerAdvice(new GlobalExceptionHandler(
                        auditLogService,
                        new SensitiveLogSanitizer()
                ))
                .build();
    }

    @Test
    void shouldRecordCreateModelConfigSuccess()
            throws Exception {
        when(modelConfigService.createConfig(any()))
                .thenReturn(modelConfigResponse(101L));

        mockMvc.perform(post("/api/admin/model-configs")
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("sk-secret-create")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(101));

        AuditLog auditLog = capturedAuditLog();
        assertAll(
                () -> assertEquals("ADMIN_OPERATION",
                        auditLog.getOperationType()),
                () -> assertEquals("MODEL_CONFIG", auditLog.getModule()),
                () -> assertEquals("INFO", auditLog.getLevel()),
                () -> assertEquals("SUCCESS", auditLog.getResult()),
                () -> assertEquals(1L, auditLog.getOperatorUserId()),
                () -> assertEquals("admin", auditLog.getOperatorUsername()),
                () -> assertEquals("ADMIN", auditLog.getOperatorRole()),
                () -> assertEquals("MODEL_CONFIG",
                        auditLog.getObjectType()),
                () -> assertEquals("101", auditLog.getObjectId()),
                () -> assertEquals("POST", auditLog.getRequestMethod()),
                () -> assertFalse(auditLog.getMetadata()
                        .contains("sk-secret-create")),
                () -> assertFalse(auditLog.toString()
                        .contains("sk-secret-create"))
        );
    }

    @Test
    void shouldRecordUpdateWithCorrectObjectId()
            throws Exception {
        when(modelConfigService.updateConfig(eq(202L), any()))
                .thenReturn(modelConfigResponse(202L));

        mockMvc.perform(put("/api/admin/model-configs/202")
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(202));

        AuditLog auditLog = capturedAuditLog();
        assertAll(
                () -> assertEquals("MODEL_CONFIG",
                        auditLog.getObjectType()),
                () -> assertEquals("202", auditLog.getObjectId()),
                () -> assertEquals("SUCCESS", auditLog.getResult()),
                () -> assertEquals("PUT", auditLog.getRequestMethod()),
                () -> assertFalse(auditLog.getMetadata()
                        .contains("sk-"))
        );
    }

    @Test
    void shouldRecordSceneBindingWithTraceableObjectId()
            throws Exception {
        when(modelConfigService.bindScene(any()))
                .thenReturn(sceneBindingResponse(303L));

        mockMvc.perform(put("/api/admin/model-configs/scene-bindings")
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SceneBindingRequest(
                                        "STORE_RECOMMENDATION",
                                        202L
                                )
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(303));

        AuditLog auditLog = capturedAuditLog();
        assertAll(
                () -> assertEquals("MODEL_SCENE_BINDING",
                        auditLog.getObjectType()),
                () -> assertEquals("303", auditLog.getObjectId()),
                () -> assertEquals("SUCCESS", auditLog.getResult()),
                () -> assertFalse(auditLog.toString()
                        .contains("Authorization"))
        );
    }

    @Test
    void shouldRecordConnectionTestFailureAsAdminOperationFailure()
            throws Exception {
        when(modelConfigService.testConfig(202L))
                .thenReturn(new ConnectionTestResponse(
                        false,
                        "Model service returned HTTP 401",
                        401
                ));

        mockMvc.perform(post("/api/admin/model-configs/202/test")
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.success").value(false));

        AuditLog auditLog = capturedAuditLog();
        assertAll(
                () -> assertEquals("WARN", auditLog.getLevel()),
                () -> assertEquals("FAILURE", auditLog.getResult()),
                () -> assertEquals("MODEL_CONFIG",
                        auditLog.getObjectType()),
                () -> assertEquals("202", auditLog.getObjectId())
        );
    }

    @Test
    void shouldKeepResponseWhenAuditWriteFails()
            throws Exception {
        when(modelConfigService.createConfig(any()))
                .thenReturn(modelConfigResponse(101L));
        doThrow(new RuntimeException("audit down"))
                .when(auditLogService)
                .recordSafely(any(AuditLog.class));

        mockMvc.perform(post("/api/admin/model-configs")
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("sk-secret-create")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(101));
    }

    @Test
    void shouldRejectUserRoleWithoutAdminOperationLog()
            throws Exception {
        mockMvc.perform(post("/api/admin/model-configs")
                        .requestAttr("userId", 2L)
                        .requestAttr("username", "user")
                        .requestAttr("role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("sk-secret-create")
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(modelConfigService, never()).createConfig(any());
    }

    private AuditLog capturedAuditLog() {
        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).recordSafely(captor.capture());
        return captor.getValue();
    }

    private ModelConfigRequest request(String apiKey) {
        return new ModelConfigRequest(
                "Main model",
                "OpenAI",
                "gpt-test",
                "https://model.example.com",
                apiKey,
                5000,
                BigDecimal.valueOf(0.7),
                4096,
                "ACTIVE"
        );
    }

    private ModelConfigResponse modelConfigResponse(Long id) {
        OffsetDateTime now = OffsetDateTime.parse(
                "2026-07-17T12:00:00+08:00"
        );
        return new ModelConfigResponse(
                id,
                "Main model",
                "OpenAI",
                "gpt-test",
                "https://model.example.com",
                "sk-t****tail",
                5000,
                BigDecimal.valueOf(0.7),
                4096,
                "ACTIVE",
                null,
                null,
                null,
                now,
                now
        );
    }

    private SceneBindingResponse sceneBindingResponse(Long id) {
        return new SceneBindingResponse(
                id,
                "STORE_RECOMMENDATION",
                202L,
                "Main model",
                "gpt-test",
                "ACTIVE",
                OffsetDateTime.parse("2026-07-17T12:00:00+08:00")
        );
    }
}
