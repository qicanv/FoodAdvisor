package com.foodadvisor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewLike;
import com.foodadvisor.entity.User;
import com.foodadvisor.entity.UserActivity;
import com.foodadvisor.entity.UserFollow;
import com.foodadvisor.mapper.ReviewLikeMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.mapper.UserActivityMapper;
import com.foodadvisor.mapper.UserFollowMapper;
import com.foodadvisor.mapper.UserMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserMapper userMapper;
    private final ReviewMapper reviewMapper;
    private final ReviewLikeMapper reviewLikeMapper;
    private final UserFollowMapper userFollowMapper;
    private final UserActivityMapper userActivityMapper;

    public AdminUserController(
            UserMapper userMapper,
            ReviewMapper reviewMapper,
            ReviewLikeMapper reviewLikeMapper,
            UserFollowMapper userFollowMapper,
            UserActivityMapper userActivityMapper
    ) {
        this.userMapper = userMapper;
        this.reviewMapper = reviewMapper;
        this.reviewLikeMapper = reviewLikeMapper;
        this.userFollowMapper = userFollowMapper;
        this.userActivityMapper = userActivityMapper;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        
        if (role != null && !role.isEmpty()) {
            wrapper.eq(User::getRole, role);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(User::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)
                    .or()
                    .like(User::getNickname, keyword)
                    .or()
                    .like(User::getPhone, keyword)
            );
        }
        
        wrapper.orderByDesc(User::getCreatedAt);
        
        Page<User> page = new Page<>(pageNum, pageSize);
        Page<User> result = userMapper.selectPage(page, wrapper);
        
        List<Map<String, Object>> records = result.getRecords().stream()
                .map(this::buildUserWithStats)
                .collect(Collectors.toList());
        
        Map<String, Object> pageResult = new LinkedHashMap<>();
        pageResult.put("records", records);
        pageResult.put("total", result.getTotal());
        pageResult.put("size", result.getSize());
        pageResult.put("current", result.getCurrent());
        pageResult.put("pages", result.getPages());
        
        return ApiResponse.success(pageResult);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getUserStats() {
        long total = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "USER"));
        long active = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "USER").eq(User::getStatus, "ACTIVE"));
        long disabled = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "USER").eq(User::getStatus, "DISABLED"));
        long locked = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, "USER").eq(User::getStatus, "LOCKED"));
        
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("active", active);
        stats.put("disabled", disabled);
        stats.put("locked", locked);
        
        return ApiResponse.success(stats);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getUserById(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ApiResponse.failure("NOT_FOUND", "用户不存在");
        }
        
        Map<String, Object> userData = buildUserWithStats(user);
        
        List<UserActivity> activities = userActivityMapper.selectList(
                new LambdaQueryWrapper<UserActivity>()
                        .eq(UserActivity::getUserId, id)
                        .orderByDesc(UserActivity::getCreatedAt)
                        .last("LIMIT 10")
        );
        
        List<Map<String, Object>> activityList = activities.stream()
                .map(this::buildActivityMap)
                .collect(Collectors.toList());
        userData.put("recentActivities", activityList);
        
        Map<Integer, Integer> ratingDist = getRatingDistribution(id);
        userData.put("ratingDistribution", ratingDist);
        
        return ApiResponse.success(userData);
    }

    @GetMapping("/{id}/activities")
    public ApiResponse<List<Map<String, Object>>> getUserActivities(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        List<UserActivity> activities = userActivityMapper.selectList(
                new LambdaQueryWrapper<UserActivity>()
                        .eq(UserActivity::getUserId, id)
                        .orderByDesc(UserActivity::getCreatedAt)
                        .last("LIMIT " + limit)
        );
        
        List<Map<String, Object>> activityList = activities.stream()
                .map(this::buildActivityMap)
                .collect(Collectors.toList());
        
        return ApiResponse.success(activityList);
    }

    @GetMapping("/{id}/rating-distribution")
    public ApiResponse<Map<Integer, Integer>> getUserRatingDistribution(@PathVariable Long id) {
        Map<Integer, Integer> ratingDist = getRatingDistribution(id);
        return ApiResponse.success(ratingDist);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<User> updateUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String status = body.get("status");
        if (status == null || status.isEmpty()) {
            return ApiResponse.failure("ERROR", "状态不能为空");
        }
        
        User user = userMapper.selectById(id);
        if (user == null) {
            return ApiResponse.failure("NOT_FOUND", "用户不存在");
        }
        
        user.setStatus(status);
        userMapper.updateById(user);
        
        return ApiResponse.success(user);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ApiResponse.failure("NOT_FOUND", "用户不存在");
        }
        
        userMapper.deleteById(id);
        
        return ApiResponse.success(null);
    }

    private Map<String, Object> buildUserWithStats(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("role", user.getRole());
        map.put("status", user.getStatus());
        map.put("createdAt", user.getCreatedAt());
        map.put("updatedAt", user.getUpdatedAt());
        map.put("lastLoginAt", user.getLastLoginAt());
        
        long reviewCount = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getUserId, user.getId())
        );
        map.put("reviewCount", reviewCount);
        
        long followCount = userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getUserId, user.getId())
        );
        map.put("followCount", followCount);
        
        long likeCount = reviewLikeMapper.selectCount(
                new LambdaQueryWrapper<ReviewLike>().eq(ReviewLike::getUserId, user.getId())
        );
        map.put("likeCount", likeCount);
        
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>().eq(Review::getUserId, user.getId())
        );
        double avgRating = reviews.stream()
                .filter(r -> r.getRating() != null)
                .mapToDouble(r -> r.getRating().doubleValue())
                .average()
                .orElse(0.0);
        map.put("avgRating", BigDecimal.valueOf(avgRating).setScale(1, RoundingMode.HALF_UP).doubleValue());
        
        return map;
    }

    private Map<String, Object> buildActivityMap(UserActivity activity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", activity.getId());
        map.put("type", activity.getActivityType().toLowerCase());
        map.put("content", activity.getContent());
        map.put("time", activity.getCreatedAt());
        return map;
    }

    private Map<Integer, Integer> getRatingDistribution(Long userId) {
        Map<Integer, Integer> ratingDist = new LinkedHashMap<>();
        ratingDist.put(5, 0);
        ratingDist.put(4, 0);
        ratingDist.put(3, 0);
        ratingDist.put(2, 0);
        ratingDist.put(1, 0);
        
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>().eq(Review::getUserId, userId)
        );
        
        for (Review review : reviews) {
            if (review.getRating() != null) {
                int rating = review.getRating().intValue();
                ratingDist.merge(rating, 1, Integer::sum);
            }
        }
        
        return ratingDist;
    }
}