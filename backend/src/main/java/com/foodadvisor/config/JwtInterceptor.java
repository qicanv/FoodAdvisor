package com.foodadvisor.config;

import com.foodadvisor.backend.common.ApiResponse;
import com.foodadvisor.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null || token.isEmpty()) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            PrintWriter writer = response.getWriter();
            writer.write(objectMapper.writeValueAsString(ApiResponse.failure("UNAUTHORIZED", "未登录或登录已过期")));
            writer.flush();
            writer.close();
            return false;
        }

        try {
            if (!JwtUtil.isTokenValid(token)) {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(401);
                PrintWriter writer = response.getWriter();
                writer.write(objectMapper.writeValueAsString(ApiResponse.failure("UNAUTHORIZED", "登录已过期")));
                writer.flush();
                writer.close();
                return false;
            }

            Long userId = JwtUtil.getUserIdFromToken(token);
            request.setAttribute("userId", userId);
            request.setAttribute("username", JwtUtil.getUsernameFromToken(token));
            request.setAttribute("role", JwtUtil.getRoleFromToken(token));
            return true;
        } catch (Exception e) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            PrintWriter writer = response.getWriter();
            writer.write(objectMapper.writeValueAsString(ApiResponse.failure("UNAUTHORIZED", "token无效")));
            writer.flush();
            writer.close();
            return false;
        }
    }
}