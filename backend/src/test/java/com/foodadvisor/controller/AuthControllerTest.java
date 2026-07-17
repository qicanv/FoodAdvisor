package com.foodadvisor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.entity.User;
import com.foodadvisor.mapper.UserMapper;
import com.foodadvisor.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AuthController(
                                userMapper,
                                auditLogService
                        )
                )
                .build();
    }

    @Test
    void shouldRecordLoginSuccess() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(activeUser());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "password", "correct-password",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.token").exists());

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).recordSafely(captor.capture());

        AuditLog auditLog = captor.getValue();
        assertAll(
                () -> assertEquals("LOGIN", auditLog.getOperationType()),
                () -> assertEquals("AUTH", auditLog.getModule()),
                () -> assertEquals("INFO", auditLog.getLevel()),
                () -> assertEquals("SUCCESS", auditLog.getResult()),
                () -> assertEquals(7L, auditLog.getOperatorUserId()),
                () -> assertEquals("alice", auditLog.getOperatorUsername()),
                () -> assertEquals("ADMIN", auditLog.getOperatorRole()),
                () -> assertEquals("USER", auditLog.getObjectType()),
                () -> assertEquals("7", auditLog.getObjectId())
        );
    }

    @Test
    void shouldRecordLoginFailureWithoutSensitiveValues()
            throws Exception {
        when(userMapper.selectOne(any())).thenReturn(activeUser());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer secret-token")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "password", "wrong-password"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ERROR"));

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).recordSafely(captor.capture());

        AuditLog auditLog = captor.getValue();
        String serializedLog = objectMapper.writeValueAsString(auditLog);

        assertAll(
                () -> assertEquals("LOGIN", auditLog.getOperationType()),
                () -> assertEquals("AUTH", auditLog.getModule()),
                () -> assertEquals("WARN", auditLog.getLevel()),
                () -> assertEquals("FAILURE", auditLog.getResult()),
                () -> assertEquals("LOGIN_INVALID_CREDENTIALS",
                        auditLog.getErrorCode()),
                () -> assertFalse(serializedLog.contains("wrong-password")),
                () -> assertFalse(serializedLog.contains("secret-token")),
                () -> assertFalse(serializedLog.contains("Bearer"))
        );
    }

    @Test
    void shouldNotDuplicateLoginFailureLog() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(null);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "missing",
                                "password", "whatever"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ERROR"));

        verify(auditLogService, times(1))
                .recordSafely(any(AuditLog.class));
    }

    @Test
    void shouldKeepLoginResponseWhenAuditWriteFails()
            throws Exception {
        when(userMapper.selectOne(any())).thenReturn(activeUser());
        doThrow(new RuntimeException("audit down"))
                .when(auditLogService)
                .recordSafely(any(AuditLog.class));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "password", "correct-password"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.token").exists());
    }

    private User activeUser() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setEmail("alice@example.com");
        user.setRole("ADMIN");
        user.setStatus("ACTIVE");
        user.setPasswordHash(
                BCrypt.hashpw("correct-password", BCrypt.gensalt())
        );
        return user;
    }
}
