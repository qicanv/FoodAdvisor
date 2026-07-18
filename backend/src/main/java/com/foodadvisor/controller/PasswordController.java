package com.foodadvisor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.entity.User;
import com.foodadvisor.mapper.UserMapper;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/password")
public class PasswordController {

    private final UserMapper userMapper;

    public PasswordController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/hash")
    public ApiResponse<Map<String, String>> generateHash(@RequestParam String password) {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        return ApiResponse.success(Map.of("password", password, "hash", hash));
    }

    @PostMapping("/reset")
    public ApiResponse<String> resetPassword(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String newPassword = body.get("newPassword");

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            return ApiResponse.failure("ERROR", "用户不存在");
        }

        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        System.out.println("Generated hash for " + username + ": " + newHash);
        user.setPasswordHash(newHash);
        userMapper.updateById(user);

        return ApiResponse.success("密码重置成功");
    }
}