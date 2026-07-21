package com.foodadvisor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.prompt.CreatePromptVersionRequest;
import com.foodadvisor.dto.prompt.PromptDefinitionResponse;
import com.foodadvisor.dto.prompt.PromptVersionResponse;
import com.foodadvisor.dto.prompt.PromptVersionSwitchRequest;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.AuditLogService;
import com.foodadvisor.service.PromptManagementService;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PromptManagementControllerTest {

    private static final String PROMPT_CONTENT =
            "You are a review summary assistant. "
                    + "Return only structured review conclusions.";

    @Mock
    private PromptManagementService promptManagementService;

    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PromptManagementController(
                        promptManagementService,
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
    void shouldListPromptDefinitionsForAdministrator()
            throws Exception {
        when(promptManagementService.listDefinitions())
                .thenReturn(List.of(definitionResponse()));

        mockMvc.perform(get("/api/admin/prompts")
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].sceneCode")
                        .value("REVIEW_SUMMARY"))
                .andExpect(jsonPath("$.data[0].activeVersionTag")
                        .value("review-summary:v1"));

        verify(promptManagementService).listDefinitions();
    }

    @Test
    void shouldCreatePromptVersionAndWriteSafeAuditLog()
            throws Exception {
        when(promptManagementService.createVersion(
                eq("REVIEW_SUMMARY"),
                any(CreatePromptVersionRequest.class),
                eq(1L)
        )).thenReturn(versionResponse(11L, 1, true));

        CreatePromptVersionRequest request =
                new CreatePromptVersionRequest(
                        PROMPT_CONTENT,
                        "Initial review summary prompt",
                        true
                );

        mockMvc.perform(post(
                        "/api/admin/prompts/REVIEW_SUMMARY/versions"
                )
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.versionTag")
                        .value("review-summary:v1"))
                .andExpect(jsonPath("$.data.active").value(true));

        AuditLog auditLog = capturedAuditLog();

        assertAll(
                () -> assertEquals(
                        "ADMIN_OPERATION",
                        auditLog.getOperationType()
                ),
                () -> assertEquals(
                        "PROMPT_MANAGEMENT",
                        auditLog.getModule()
                ),
                () -> assertEquals(
                        "PROMPT_VERSION",
                        auditLog.getObjectType()
                ),
                () -> assertEquals(
                        "11",
                        auditLog.getObjectId()
                ),
                () -> assertEquals(
                        "SUCCESS",
                        auditLog.getResult()
                ),
                () -> assertTrue(
                        auditLog.getMetadata()
                                .contains("CREATE_PROMPT_VERSION")
                ),
                () -> assertTrue(
                        auditLog.getMetadata()
                                .contains("\"contentLength\":")
                ),
                () -> assertFalse(
                        auditLog.getMetadata()
                                .contains(PROMPT_CONTENT)
                ),
                () -> assertFalse(
                        auditLog.toString()
                                .contains(PROMPT_CONTENT)
                )
        );
    }

    @Test
    void shouldActivatePromptVersion()
            throws Exception {
        when(promptManagementService.activateVersion(
                eq("REVIEW_SUMMARY"),
                eq(11L),
                any(PromptVersionSwitchRequest.class),
                eq(1L)
        )).thenReturn(versionResponse(11L, 1, true));

        PromptVersionSwitchRequest request =
                new PromptVersionSwitchRequest(
                        "Enable after manual verification"
                );

        mockMvc.perform(post(
                        "/api/admin/prompts/"
                                + "REVIEW_SUMMARY/versions/11/activate"
                )
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.active").value(true));

        AuditLog auditLog = capturedAuditLog();

        assertAll(
                () -> assertEquals(
                        "PROMPT_MANAGEMENT",
                        auditLog.getModule()
                ),
                () -> assertTrue(
                        auditLog.getMetadata()
                                .contains("ACTIVATE_PROMPT_VERSION")
                ),
                () -> assertFalse(
                        auditLog.getMetadata()
                                .contains(PROMPT_CONTENT)
                )
        );
    }

    @Test
    void shouldRollbackPromptVersion()
            throws Exception {
        when(promptManagementService.rollbackVersion(
                eq("REVIEW_SUMMARY"),
                eq(11L),
                any(PromptVersionSwitchRequest.class),
                eq(1L)
        )).thenReturn(versionResponse(11L, 1, true));

        PromptVersionSwitchRequest request =
                new PromptVersionSwitchRequest(
                        "Rollback because version 2 was unstable"
                );

        mockMvc.perform(post(
                        "/api/admin/prompts/"
                                + "REVIEW_SUMMARY/versions/11/rollback"
                )
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.active").value(true));

        AuditLog auditLog = capturedAuditLog();

        assertTrue(
                auditLog.getMetadata()
                        .contains("ROLLBACK_PROMPT_VERSION")
        );
    }

    @Test
    void shouldRejectNonAdministratorWithoutCreatingVersion()
            throws Exception {
        CreatePromptVersionRequest request =
                new CreatePromptVersionRequest(
                        PROMPT_CONTENT,
                        "Unauthorized attempt",
                        true
                );

        mockMvc.perform(post(
                        "/api/admin/prompts/REVIEW_SUMMARY/versions"
                )
                        .requestAttr("userId", 2L)
                        .requestAttr("username", "user")
                        .requestAttr("role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(
                promptManagementService,
                never()
        ).createVersion(any(), any(), any());
    }

    @Test
    void shouldKeepSuccessfulResponseWhenAuditWriteFails()
            throws Exception {
        when(promptManagementService.createVersion(
                eq("REVIEW_SUMMARY"),
                any(CreatePromptVersionRequest.class),
                eq(1L)
        )).thenReturn(versionResponse(11L, 1, true));

        doThrow(new RuntimeException("audit service unavailable"))
                .when(auditLogService)
                .recordSafely(any(AuditLog.class));

        CreatePromptVersionRequest request =
                new CreatePromptVersionRequest(
                        PROMPT_CONTENT,
                        "Initial version",
                        true
                );

        mockMvc.perform(post(
                        "/api/admin/prompts/REVIEW_SUMMARY/versions"
                )
                        .requestAttr("userId", 1L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(11));
    }

    private AuditLog capturedAuditLog() {
        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);

        verify(auditLogService)
                .recordSafely(captor.capture());

        return captor.getValue();
    }

    private PromptDefinitionResponse definitionResponse() {
        OffsetDateTime now = OffsetDateTime.parse(
                "2026-07-21T17:00:00+08:00"
        );

        return new PromptDefinitionResponse(
                3L,
                "REVIEW_SUMMARY",
                "评价摘要",
                "Generates structured review summaries.",
                "ACTIVE",
                11L,
                1,
                "review-summary:v1",
                PROMPT_CONTENT,
                now,
                now
        );
    }

    private PromptVersionResponse versionResponse(
            Long id,
            int versionNo,
            boolean active
    ) {
        return new PromptVersionResponse(
                id,
                3L,
                "REVIEW_SUMMARY",
                versionNo,
                "review-summary:v" + versionNo,
                PROMPT_CONTENT,
                "Initial review summary prompt",
                1L,
                OffsetDateTime.parse(
                        "2026-07-21T17:00:00+08:00"
                ),
                active
        );
    }
}