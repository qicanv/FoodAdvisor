package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.audit.AuditLogQueryRequest;
import com.foodadvisor.dto.audit.AuditLogVO;
import com.foodadvisor.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/logs")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;
    private final AdminAccessGuard adminAccessGuard;

    public AdminAuditLogController(
            AuditLogService auditLogService,
            AdminAccessGuard adminAccessGuard
    ) {
        this.auditLogService = auditLogService;
        this.adminAccessGuard = adminAccessGuard;
    }

    @GetMapping
    public ApiResponse<PageResult<AuditLogVO>> list(
            @ModelAttribute AuditLogQueryRequest query,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);
        return ApiResponse.success(
                PageResult.from(auditLogService.query(query))
        );
    }
}
