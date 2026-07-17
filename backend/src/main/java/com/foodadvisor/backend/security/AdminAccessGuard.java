package com.foodadvisor.backend.security;

import com.foodadvisor.backend.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessGuard {

    public void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Only platform administrators can access model configuration"
            );
        }
    }

    public void requireAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        requireAdmin(role == null ? null : role.toString());
    }
}
