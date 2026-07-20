package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.trace.AiTraceDetailVO;
import com.foodadvisor.dto.trace.AiTraceQueryRequest;
import com.foodadvisor.entity.AiRequestTrace;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.security.TraceAccessGuard;
import com.foodadvisor.service.AiRequestTraceService;
import com.foodadvisor.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai-traces")
public class AdminAiTraceController {
    private final AiRequestTraceService traceService;
    private final TraceAccessGuard accessGuard;
    private final AuditLogService auditLogService;

    public AdminAiTraceController(AiRequestTraceService traceService,
                                  TraceAccessGuard accessGuard,
                                  AuditLogService auditLogService) {
        this.traceService = traceService;
        this.accessGuard = accessGuard;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<PageResult<AiRequestTrace>> list(
            @ModelAttribute AiTraceQueryRequest query, HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);
        return ApiResponse.success(PageResult.from(traceService.query(query)));
    }

    @GetMapping("/{traceId}")
    public ApiResponse<AiTraceDetailVO> detail(
            @PathVariable String traceId, HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);
        AiTraceDetailVO detail = traceService.detail(traceId);
        recordView(request, traceId);
        return ApiResponse.success(detail);
    }

    private void recordView(HttpServletRequest request, String traceId) {
        AuditLog log = new AuditLog();
        log.setOperationType("ADMIN_OPERATION");
        log.setModule("AI_TRACE");
        log.setLevel("INFO");
        log.setResult("SUCCESS");
        log.setObjectType("AI_REQUEST_TRACE");
        log.setObjectId(traceId);
        log.setBusinessTraceId(traceId);
        log.setOperatorUserId(toLong(request.getAttribute("userId")));
        log.setOperatorUsername(text(request.getAttribute("username")));
        log.setOperatorRole(text(request.getAttribute("role")));
        log.setRequestMethod(request.getMethod());
        log.setRequestUri(request.getRequestURI());
        log.setMetadata("{\"action\":\"VIEW_TRACE_DETAIL\"}");
        auditLogService.recordSafely(log);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }
    private String text(Object value) { return value == null ? null : value.toString(); }
}
