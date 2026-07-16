package com.foodadvisor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.backend.common.ApiResponse;
import com.foodadvisor.entity.User;
import com.foodadvisor.mapper.UserMapper;
import com.foodadvisor.util.JwtUtil;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;

    public AuthController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");

            System.out.println("Login attempt - username: " + username + ", password: " + (password != null ? "***" : "null"));

            if (username == null || username.isEmpty()) {
                return ApiResponse.failure("ERROR", "用户名不能为空");
            }

            if (password == null || password.isEmpty()) {
                return ApiResponse.failure("ERROR", "密码不能为空");
            }

            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, username);
            User user = userMapper.selectOne(wrapper);

            if (user == null) {
                System.out.println("User not found: " + username);
                return ApiResponse.failure("ERROR", "用户名不存在");
            }

            System.out.println("User found: " + user.getUsername() + ", status: " + user.getStatus());
            System.out.println("Stored password hash: " + user.getPasswordHash());

            if ("DISABLED".equals(user.getStatus())) {
                return ApiResponse.failure("ERROR", "账号已被禁用");
            }

            if ("LOCKED".equals(user.getStatus())) {
                return ApiResponse.failure("ERROR", "账号已被锁定");
            }

            if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
                return ApiResponse.failure("ERROR", "用户密码未设置");
            }

            boolean passwordMatch = BCrypt.checkpw(password, user.getPasswordHash());
            System.out.println("Password match: " + passwordMatch);

            if (!passwordMatch) {
                return ApiResponse.failure("ERROR", "密码错误");
            }

            String token = JwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

            return ApiResponse.success(Map.of(
                    "token", token,
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "nickname", user.getNickname(),
                    "email", user.getEmail(),
                    "role", user.getRole(),
                    "status", user.getStatus()
            ));
        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
            e.printStackTrace();
            return ApiResponse.failure("ERROR", "登录失败：" + e.getMessage());
        }
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String confirmPassword = body.get("confirmPassword");
        String email = body.get("email");
        String nickname = body.get("nickname");
        String role = body.get("role");

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User existingUser = userMapper.selectOne(wrapper);

        if (existingUser != null) {
            return ApiResponse.failure("ERROR", "用户名已存在");
        }

        if (!password.equals(confirmPassword)) {
            return ApiResponse.failure("ERROR", "两次输入的密码不一致");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setNickname(nickname != null ? nickname : username);
        user.setEmail(email);
        user.setRole(role != null ? role : "USER");
        user.setStatus("ACTIVE");

        userMapper.insert(user);

        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        return ApiResponse.success(Map.of(
                "token", token,
                "userId", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "status", user.getStatus()
        ));
    }
}