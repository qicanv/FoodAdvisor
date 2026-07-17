package com.foodadvisor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.backend.common.ApiResponse;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.entity.User;
import com.foodadvisor.mapper.UserMapper;
import com.foodadvisor.service.AuditLogService;
import com.foodadvisor.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log =
            LoggerFactory.getLogger(AuthController.class);

    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    public AuthController(
            UserMapper userMapper,
            AuditLogService auditLogService
    ) {
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        String requiredRole = body == null ? null : body.get("role");

        try {
            if (username == null || username.isEmpty()) {
                recordLoginFailure(
                        username,
                        null,
                        "LOGIN_INVALID_CREDENTIALS",
                        request
                );
                return ApiResponse.failure("ERROR", "用户名不能为空");
            }

            if (password == null || password.isEmpty()) {
                recordLoginFailure(
                        username,
                        null,
                        "LOGIN_INVALID_CREDENTIALS",
                        request
                );
                return ApiResponse.failure("ERROR", "密码不能为空");
            }

            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getUsername, username)
            );

            if (user == null) {
                recordLoginFailure(
                        username,
                        null,
                        "LOGIN_INVALID_CREDENTIALS",
                        request
                );
                return ApiResponse.failure("ERROR", "用户名不存在");
            }

            if ("DISABLED".equals(user.getStatus())) {
                recordLoginFailure(
                        user.getUsername(),
                        user,
                        "LOGIN_ACCOUNT_UNAVAILABLE",
                        request
                );
                return ApiResponse.failure("ERROR", "账号已被禁用");
            }

            if ("LOCKED".equals(user.getStatus())) {
                recordLoginFailure(
                        user.getUsername(),
                        user,
                        "LOGIN_ACCOUNT_UNAVAILABLE",
                        request
                );
                return ApiResponse.failure("ERROR", "账号已被锁定");
            }

            if (user.getPasswordHash() == null
                    || user.getPasswordHash().isEmpty()) {
                recordLoginFailure(
                        user.getUsername(),
                        user,
                        "LOGIN_ACCOUNT_UNAVAILABLE",
                        request
                );
                return ApiResponse.failure("ERROR", "用户密码未设置");
            }

            boolean passwordMatch = BCrypt.checkpw(
                    password,
                    user.getPasswordHash()
            );

            if (!passwordMatch) {
                recordLoginFailure(
                        user.getUsername(),
                        user,
                        "LOGIN_INVALID_CREDENTIALS",
                        request
                );
                return ApiResponse.failure("ERROR", "密码错误");
            }

            if (requiredRole != null
                    && !requiredRole.isEmpty()
                    && !requiredRole.equals(user.getRole())) {
                recordLoginFailure(
                        user.getUsername(),
                        user,
                        "LOGIN_ROLE_MISMATCH",
                        request
                );
                return ApiResponse.failure(
                        "ERROR",
                        "该账号不属于此端，请使用正确的端口登录"
                );
            }

            String token = JwtUtil.generateToken(
                    user.getId(),
                    user.getUsername(),
                    user.getRole()
            );

            recordLoginSuccess(user, request);

            return ApiResponse.success(buildAuthData(token, user));
        } catch (Exception exception) {
            log.warn(
                    "Login failed unexpectedly for username={}",
                    username
            );
            recordLoginFailure(
                    username,
                    null,
                    "LOGIN_INTERNAL_ERROR",
                    request
            );

            return ApiResponse.failure(
                    "ERROR",
                    "登录失败：" + exception.getMessage()
            );
        }
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(
            @RequestBody Map<String, String> body
    ) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            String confirmPassword = body.get("confirmPassword");
            String email = body.get("email");
            String nickname = body.get("nickname");
            String role = body.get("role");

            log.info(
                    "Register attempt - username={}, email={}, role={}",
                    username,
                    email,
                    role
            );

            if (username == null || username.isEmpty()) {
                return ApiResponse.failure("ERROR", "用户名不能为空");
            }

            if (password == null || password.isEmpty()) {
                return ApiResponse.failure("ERROR", "密码不能为空");
            }

            if (confirmPassword == null
                    || confirmPassword.isEmpty()) {
                return ApiResponse.failure("ERROR", "确认密码不能为空");
            }

            User existingUser = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getUsername, username)
            );

            if (existingUser != null) {
                log.info("Username already exists: {}", username);
                return ApiResponse.failure("ERROR", "用户名已存在");
            }

            if (!password.equals(confirmPassword)) {
                return ApiResponse.failure("ERROR", "两次输入的密码不一致");
            }

            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(
                    BCrypt.hashpw(password, BCrypt.gensalt())
            );
            user.setNickname(
                    nickname != null ? nickname : username
            );
            user.setEmail(email);
            user.setRole(role != null ? role : "USER");
            user.setStatus("ACTIVE");

            userMapper.insert(user);

            log.info(
                    "User registered successfully: {}",
                    username
            );

            String token = JwtUtil.generateToken(
                    user.getId(),
                    user.getUsername(),
                    user.getRole()
            );

            return ApiResponse.success(buildAuthData(token, user));
        } catch (Exception exception) {
            log.warn(
                    "Register error: {}",
                    exception.getMessage()
            );

            return ApiResponse.failure(
                    "ERROR",
                    "注册失败：" + exception.getMessage()
            );
        }
    }

    private Map<String, Object> buildAuthData(
            String token,
            User user
    ) {
        Map<String, Object> data =
                new LinkedHashMap<>();

        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        data.put("status", user.getStatus());

        return data;
    }

    private void recordLoginSuccess(
            User user,
            HttpServletRequest request
    ) {
        AuditLog auditLog = baseLoginLog(
                user.getUsername(),
                user,
                request
        );
        auditLog.setLevel("INFO");
        auditLog.setResult("SUCCESS");
        auditLog.setObjectType("USER");
        auditLog.setObjectId(String.valueOf(user.getId()));
        auditLog.setMetadata("{\"reason\":\"LOGIN_SUCCESS\"}");
        safeRecord(auditLog);
    }

    private void recordLoginFailure(
            String username,
            User user,
            String reason,
            HttpServletRequest request
    ) {
        AuditLog auditLog = baseLoginLog(username, user, request);
        auditLog.setLevel("WARN");
        auditLog.setResult("FAILURE");
        auditLog.setErrorCode(reason);
        auditLog.setErrorMessage("Login failed");
        auditLog.setMetadata(
                "{\"reason\":\"" + reason + "\"}"
        );

        if (user != null && user.getId() != null) {
            auditLog.setObjectType("USER");
            auditLog.setObjectId(String.valueOf(user.getId()));
        }

        safeRecord(auditLog);
    }

    private AuditLog baseLoginLog(
            String username,
            User user,
            HttpServletRequest request
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperationType("LOGIN");
        auditLog.setModule("AUTH");
        auditLog.setOperatorUserId(user == null ? null : user.getId());
        auditLog.setOperatorUsername(
                user == null ? username : user.getUsername()
        );
        auditLog.setOperatorRole(user == null ? null : user.getRole());
        auditLog.setRequestMethod(request.getMethod());
        auditLog.setRequestUri(request.getRequestURI());
        auditLog.setIpAddress(clientIp(request));
        auditLog.setUserAgent(request.getHeader("User-Agent"));
        return auditLog;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void safeRecord(AuditLog auditLog) {
        try {
            auditLogService.recordSafely(auditLog);
        } catch (Exception exception) {
            log.warn(
                    "Login audit logging failed: {}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
