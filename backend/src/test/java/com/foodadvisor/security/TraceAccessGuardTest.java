package com.foodadvisor.security;

import com.foodadvisor.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceAccessGuardTest {
    private final TraceAccessGuard guard = new TraceAccessGuard();

    @Test
    void allowsAdminAndOperatorFromJwtAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("role", "ADMIN");
        guard.requireOperatorOrAdmin(request);
        request.setAttribute("role", "OPERATOR");
        guard.requireOperatorOrAdmin(request);
    }

    @Test
    void headerCannotBypassJwtRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("role", "USER");
        request.addHeader("X-User-Role", "ADMIN");
        assertThatThrownBy(() -> guard.requireOperatorOrAdmin(request))
                .isInstanceOf(ApiException.class);
    }
}
