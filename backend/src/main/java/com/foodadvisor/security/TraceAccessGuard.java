package com.foodadvisor.security;

import com.foodadvisor.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TraceAccessGuard {
    public void requireOperatorOrAdmin(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("role");
        String role = value == null ? null : value.toString();
        if (!"ADMIN".equalsIgnoreCase(role) && !"OPERATOR".equalsIgnoreCase(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only administrators and operators can access AI traces");
        }
    }
}
