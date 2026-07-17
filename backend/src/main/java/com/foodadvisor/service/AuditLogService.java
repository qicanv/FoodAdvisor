package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.audit.AuditLogQueryRequest;
import com.foodadvisor.dto.audit.AuditLogVO;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.mapper.AuditLogMapper;
import com.foodadvisor.util.SensitiveLogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
public class AuditLogService {

    private static final Logger log =
            LoggerFactory.getLogger(AuditLogService.class);

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> OPERATION_TYPES = Set.of(
            "LOGIN",
            "ADMIN_OPERATION",
            "AI_CALL",
            "API_EXCEPTION",
            "DATA_IMPORT",
            "CONTENT_MODERATION"
    );

    private static final Set<String> LEVELS = Set.of(
            "INFO",
            "WARN",
            "ERROR"
    );

    private static final Set<String> RESULTS = Set.of(
            "SUCCESS",
            "FAILURE"
    );

    private final AuditLogMapper auditLogMapper;
    private final SensitiveLogSanitizer sanitizer;

    public AuditLogService(
            AuditLogMapper auditLogMapper,
            SensitiveLogSanitizer sanitizer
    ) {
        this.auditLogMapper = auditLogMapper;
        this.sanitizer = sanitizer;
    }

    public Page<AuditLogVO> query(AuditLogQueryRequest request) {
        int pageNum = request.getPageNum() <= 0
                ? 1
                : request.getPageNum();
        int pageSize = request.getPageSize() <= 0
                ? 10
                : Math.min(request.getPageSize(), MAX_PAGE_SIZE);

        Page<AuditLog> page = auditLogMapper.selectPage(
                Page.of(pageNum, pageSize),
                buildQueryWrapper(request)
        );

        Page<AuditLogVO> result = Page.of(page.getCurrent(), page.getSize());
        result.setTotal(page.getTotal());
        result.setRecords(
                page.getRecords()
                        .stream()
                        .map(this::toVO)
                        .toList()
        );
        return result;
    }

    public void record(AuditLog auditLog) {
        normalize(auditLog);
        auditLogMapper.insert(auditLog);
    }

    public void recordSafely(AuditLog auditLog) {
        try {
            record(auditLog);
        } catch (Exception exception) {
            String operationType = auditLog == null
                    ? null
                    : sanitizer.sanitize(auditLog.getOperationType());
            String module = auditLog == null
                    ? null
                    : sanitizer.sanitize(auditLog.getModule());
            String errorMessage = sanitizer.sanitize(exception.getMessage());

            log.warn(
                    "Audit log write failed. operationType={}, module={}, error={}",
                    operationType,
                    module,
                    errorMessage
            );
        }
    }

    private QueryWrapper<AuditLog> buildQueryWrapper(
            AuditLogQueryRequest request
    ) {
        QueryWrapper<AuditLog> wrapper =
                new QueryWrapper<>();

        OffsetDateTime startTime = request.getStartTime();
        OffsetDateTime endTime = request.getEndTime();

        wrapper.ge(startTime != null, "created_at", startTime)
                .le(endTime != null, "created_at", endTime)
                .eq(request.getOperatorUserId() != null,
                        "operator_user_id",
                        request.getOperatorUserId())
                .eq(hasText(request.getOperatorUsername()),
                        "operator_username",
                        trim(request.getOperatorUsername()))
                .eq(hasText(request.getModule()),
                        "module",
                        trim(request.getModule()))
                .eq(hasText(request.getLevel()),
                        "level",
                        trimUpper(request.getLevel()))
                .eq(hasText(request.getOperationType()),
                        "operation_type",
                        trimUpper(request.getOperationType()))
                .eq(hasText(request.getResult()),
                        "result",
                        trimUpper(request.getResult()))
                .eq(hasText(request.getObjectType()),
                        "object_type",
                        trim(request.getObjectType()))
                .eq(hasText(request.getObjectId()),
                        "object_id",
                        trim(request.getObjectId()))
                .orderByDesc("created_at")
                .orderByDesc("id");

        return wrapper;
    }

    private void normalize(AuditLog auditLog) {
        if (auditLog == null) {
            throw new IllegalArgumentException("auditLog is required");
        }

        auditLog.setOperationType(
                requireAllowed(
                        auditLog.getOperationType(),
                        OPERATION_TYPES,
                        "operationType"
                )
        );
        auditLog.setLevel(
                requireAllowed(auditLog.getLevel(), LEVELS, "level")
        );
        auditLog.setResult(
                requireAllowed(auditLog.getResult(), RESULTS, "result")
        );

        if (!hasText(auditLog.getModule())) {
            throw new IllegalArgumentException("module is required");
        }

        auditLog.setModule(trim(auditLog.getModule()));
        auditLog.setOperatorUsername(
                sanitizer.sanitize(auditLog.getOperatorUsername())
        );
        auditLog.setOperatorRole(trimUpper(auditLog.getOperatorRole()));
        auditLog.setObjectType(trim(auditLog.getObjectType()));
        auditLog.setObjectId(trim(auditLog.getObjectId()));
        auditLog.setErrorCode(trim(auditLog.getErrorCode()));
        auditLog.setErrorMessage(
                sanitizer.sanitize(auditLog.getErrorMessage())
        );
        auditLog.setRequestMethod(trimUpper(auditLog.getRequestMethod()));
        auditLog.setRequestUri(
                sanitizer.sanitize(auditLog.getRequestUri())
        );
        auditLog.setIpAddress(trim(auditLog.getIpAddress()));
        auditLog.setUserAgent(
                sanitizer.sanitize(auditLog.getUserAgent())
        );
        auditLog.setBusinessTraceId(
                sanitizer.sanitize(auditLog.getBusinessTraceId())
        );
        auditLog.setMetadata(
                hasText(auditLog.getMetadata())
                        ? sanitizer.sanitize(auditLog.getMetadata())
                        : "{}"
        );
    }

    private String requireAllowed(
            String value,
            Set<String> allowed,
            String field
    ) {
        String normalized = trimUpper(value);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(
                    field + " is not supported"
            );
        }
        return normalized;
    }

    private AuditLogVO toVO(AuditLog auditLog) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(auditLog.getId());
        vo.setOperationType(auditLog.getOperationType());
        vo.setOperatorUserId(auditLog.getOperatorUserId());
        vo.setOperatorUsername(auditLog.getOperatorUsername());
        vo.setOperatorRole(auditLog.getOperatorRole());
        vo.setModule(auditLog.getModule());
        vo.setLevel(auditLog.getLevel());
        vo.setResult(auditLog.getResult());
        vo.setObjectType(auditLog.getObjectType());
        vo.setObjectId(auditLog.getObjectId());
        vo.setErrorCode(auditLog.getErrorCode());
        vo.setErrorMessage(auditLog.getErrorMessage());
        vo.setRequestMethod(auditLog.getRequestMethod());
        vo.setRequestUri(auditLog.getRequestUri());
        vo.setIpAddress(auditLog.getIpAddress());
        vo.setUserAgent(auditLog.getUserAgent());
        vo.setBusinessTraceId(auditLog.getBusinessTraceId());
        vo.setMetadata(auditLog.getMetadata());
        vo.setCreatedAt(auditLog.getCreatedAt());
        return vo;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimUpper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
