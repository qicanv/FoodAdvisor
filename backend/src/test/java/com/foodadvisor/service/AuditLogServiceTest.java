package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.audit.AuditLogQueryRequest;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.mapper.AuditLogMapper;
import com.foodadvisor.util.SensitiveLogSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(
                auditLogMapper,
                new SensitiveLogSanitizer()
        );
    }

    @Test
    void shouldApplyQueryConditionsAndPagination() {
        AuditLogQueryRequest request = new AuditLogQueryRequest();
        request.setStartTime(OffsetDateTime.parse("2026-07-01T00:00:00+08:00"));
        request.setEndTime(OffsetDateTime.parse("2026-07-17T23:59:59+08:00"));
        request.setOperatorUserId(7L);
        request.setOperatorUsername("admin");
        request.setModule("MODEL_CONFIG");
        request.setLevel("error");
        request.setOperationType("admin_operation");
        request.setResult("failure");
        request.setObjectType("MODEL_CONFIG");
        request.setObjectId("3");
        request.setPageNum(2);
        request.setPageSize(20);

        Page<AuditLog> mapperPage = Page.of(2, 20);
        mapperPage.setTotal(1);
        mapperPage.setRecords(List.of(auditLog()));

        when(auditLogMapper.selectPage(
                any(Page.class),
                any(QueryWrapper.class)
        ))
                .thenReturn(mapperPage);

        auditLogService.query(request);

        ArgumentCaptor<Page<AuditLog>> pageCaptor =
                ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<QueryWrapper<AuditLog>> wrapperCaptor =
                ArgumentCaptor.forClass(QueryWrapper.class);

        verify(auditLogMapper).selectPage(
                pageCaptor.capture(),
                wrapperCaptor.capture()
        );

        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        List<Object> queryValues = wrapperCaptor.getValue()
                .getParamNameValuePairs()
                .values()
                .stream()
                .toList();

        assertAll(
                () -> assertEquals(2, pageCaptor.getValue().getCurrent()),
                () -> assertEquals(20, pageCaptor.getValue().getSize()),
                () -> assertTrue(sqlSegment.contains("created_at")),
                () -> assertTrue(sqlSegment.contains("operator_user_id")),
                () -> assertTrue(sqlSegment.contains("operator_username")),
                () -> assertTrue(sqlSegment.contains("module")),
                () -> assertTrue(sqlSegment.contains("level")),
                () -> assertTrue(sqlSegment.contains("operation_type")),
                () -> assertTrue(sqlSegment.contains("result")),
                () -> assertTrue(sqlSegment.contains("object_type")),
                () -> assertTrue(sqlSegment.contains("object_id")),
                () -> assertTrue(queryValues.contains(request.getStartTime())),
                () -> assertTrue(queryValues.contains(request.getEndTime())),
                () -> assertTrue(queryValues.contains(7L)),
                () -> assertTrue(queryValues.contains("admin")),
                () -> assertTrue(queryValues.contains("MODEL_CONFIG")),
                () -> assertTrue(queryValues.contains("ERROR")),
                () -> assertTrue(queryValues.contains("ADMIN_OPERATION")),
                () -> assertTrue(queryValues.contains("FAILURE")),
                () -> assertTrue(queryValues.contains("3"))
        );
    }

    @Test
    void shouldSanitizeBeforeRecord() {
        AuditLog auditLog = auditLog();
        auditLog.setErrorMessage("Authorization: Bearer secret-token-value");
        auditLog.setMetadata("{\"apiKey\":\"sk-1234567890abcdef\"}");

        auditLogService.record(auditLog);

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());

        assertFalse(captor.getValue().getErrorMessage()
                .contains("secret-token-value"));
        assertFalse(captor.getValue().getMetadata()
                .contains("sk-1234567890abcdef"));
    }

    @Test
    void shouldNotThrowWhenRecordSafelyFails() {
        doThrow(new RuntimeException("password=plain-secret"))
                .when(auditLogMapper)
                .insert(any(AuditLog.class));

        auditLogService.recordSafely(auditLog());
    }

    private AuditLog auditLog() {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(1L);
        auditLog.setOperationType("ADMIN_OPERATION");
        auditLog.setOperatorUserId(7L);
        auditLog.setOperatorUsername("admin");
        auditLog.setOperatorRole("ADMIN");
        auditLog.setModule("MODEL_CONFIG");
        auditLog.setLevel("INFO");
        auditLog.setResult("SUCCESS");
        auditLog.setObjectType("MODEL_CONFIG");
        auditLog.setObjectId("3");
        auditLog.setMetadata("{}");
        auditLog.setCreatedAt(
                OffsetDateTime.parse("2026-07-17T12:00:00+08:00")
        );
        return auditLog;
    }
}
