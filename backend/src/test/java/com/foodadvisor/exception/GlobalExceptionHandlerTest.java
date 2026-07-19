package com.foodadvisor.exception;

import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.service.AuditLogService;
import com.foodadvisor.util.SensitiveLogSanitizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExceptionTestController())
                .setControllerAdvice(new GlobalExceptionHandler(
                        auditLogService,
                        new SensitiveLogSanitizer()
                ))
                .build();
    }

    @Test
    void shouldRecordValidationExceptionAsWarn()
            throws Exception {
        mockMvc.perform(post("/api/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("userId", 3L)
                        .requestAttr("username", "bob")
                        .requestAttr("role", "USER")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).recordSafely(captor.capture());

        AuditLog auditLog = captor.getValue();
        assertAll(
                () -> assertEquals("API_EXCEPTION",
                        auditLog.getOperationType()),
                () -> assertEquals("WARN", auditLog.getLevel()),
                () -> assertEquals("FAILURE", auditLog.getResult()),
                () -> assertEquals("INVALID_REQUEST",
                        auditLog.getErrorCode()),
                () -> assertEquals(3L, auditLog.getOperatorUserId()),
                () -> assertEquals("bob", auditLog.getOperatorUsername()),
                () -> assertEquals("USER", auditLog.getOperatorRole())
        );
    }

    @Test
    void shouldRecordUnknownExceptionAsErrorAndSanitize()
            throws Exception {
        mockMvc.perform(post("/api/test/boom?token=query-token")
                        .header("User-Agent", "JUnit")
                        .requestAttr("userId", 9L)
                        .requestAttr("username", "admin")
                        .requestAttr("role", "ADMIN"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).recordSafely(captor.capture());

        AuditLog auditLog = captor.getValue();
        String serialized = auditLog.toString();

        assertAll(
                () -> assertEquals("API_EXCEPTION",
                        auditLog.getOperationType()),
                () -> assertEquals("ERROR", auditLog.getLevel()),
                () -> assertEquals("FAILURE", auditLog.getResult()),
                () -> assertEquals("INTERNAL_ERROR",
                        auditLog.getErrorCode()),
                () -> assertFalse(serialized.contains("plain-secret")),
                () -> assertFalse(serialized.contains("secret-token")),
                () -> assertFalse(serialized.contains("select * from users")),
                () -> assertFalse(serialized.contains("query-token"))
        );
    }

    @Test
    void shouldKeepExceptionResponseWhenAuditWriteFails()
            throws Exception {
        doThrow(new RuntimeException("audit down"))
                .when(auditLogService)
                .recordSafely(any(AuditLog.class));

        mockMvc.perform(post("/api/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Internal server error"));
    }

    @RestController
    static class ExceptionTestController {

        @PostMapping("/api/test/validate")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @PostMapping("/api/test/boom")
        void boom() {
            throw new RuntimeException(
                    "Authorization: Bearer secret-token password=plain-secret SQL [select * from users]"
            );
        }
    }

    static class TestRequest {

        @NotBlank
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
