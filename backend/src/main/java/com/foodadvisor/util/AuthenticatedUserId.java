package com.foodadvisor.util;

import com.foodadvisor.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

public final class AuthenticatedUserId {

    private AuthenticatedUserId() {
    }

    public static Long require(HttpServletRequest request) {
        Object value = request == null
                ? null
                : request.getAttribute("userId");

        Long userId = null;
        if (value instanceof Number number) {
            userId = number.longValue();
        }

        if (userId == null || userId <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "缺少有效用户身份"
            );
        }

        return userId;
    }
}
