package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.exception.GlobalExceptionHandler;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.dto.audit.AuditLogQueryRequest;
import com.foodadvisor.dto.audit.AuditLogVO;
import com.foodadvisor.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogControllerTest {

    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new AdminAuditLogController(
                                auditLogService,
                                new AdminAccessGuard()
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldAllowAdminToQueryLogs() throws Exception {
        Page<AuditLogVO> page = Page.of(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(auditLogVO()));

        when(auditLogService.query(any(AuditLogQueryRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/logs")
                        .requestAttr("role", "ADMIN")
                        .param("operationType", "ADMIN_OPERATION")
                        .param("module", "MODEL_CONFIG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].operationType")
                        .value("ADMIN_OPERATION"))
                .andExpect(jsonPath("$.data.records[0].module")
                        .value("MODEL_CONFIG"));
    }

    @Test
    void shouldRejectUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/logs")
                        .requestAttr("role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldRejectMerchantRole() throws Exception {
        mockMvc.perform(get("/api/admin/logs")
                        .requestAttr("role", "MERCHANT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private AuditLogVO auditLogVO() {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(1L);
        vo.setOperationType("ADMIN_OPERATION");
        vo.setOperatorUserId(7L);
        vo.setOperatorUsername("admin");
        vo.setOperatorRole("ADMIN");
        vo.setModule("MODEL_CONFIG");
        vo.setLevel("INFO");
        vo.setResult("SUCCESS");
        vo.setObjectType("MODEL_CONFIG");
        vo.setObjectId("3");
        vo.setMetadata("{}");
        vo.setCreatedAt(
                OffsetDateTime.parse("2026-07-17T12:00:00+08:00")
        );
        return vo;
    }
}
