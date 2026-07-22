package com.foodadvisor.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.entity.Dish;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.DishMapper;
import com.foodadvisor.service.ContentStatusService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/merchant-console/dishes")
public class MerchantDishesController {

    private static final Logger log =
            LoggerFactory.getLogger(MerchantDishesController.class);

    private static final Set<String> VALID_STATUSES =
            Set.of("ACTIVE", "OFF_SHELF", "ARCHIVED");

    private final DishMapper dishMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ContentStatusService contentStatusService;

    public MerchantDishesController(
            DishMapper dishMapper,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ContentStatusService contentStatusService
            ObjectMapper objectMapper
    ) {
        this.dishMapper = dishMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.contentStatusService = contentStatusService;
    }

    /**
     * 查询菜品列表。
     * 不传 merchantId 时返回用户所有店铺的菜品。
     */
    @GetMapping
    public ApiResponse<List<Dish>> list(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String status,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);
        List<Long> myMerchantIds = getMyMerchantIds(userId);

        if (myMerchantIds.isEmpty()) {
            return ApiResponse.success(List.of());
        }

        Set<Long> dishIds = new LinkedHashSet<>();
        List<Dish> dishes = new ArrayList<>();

        if (merchantId != null) {
            if (!myMerchantIds.contains(merchantId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "您不是该店铺的管理者");
            }
            dishes = dishMapper.selectByMerchantId(merchantId);
        } else {
            for (Long id : myMerchantIds) {
                List<Dish> storeDishes = dishMapper.selectByMerchantId(id);
                for (Dish d : storeDishes) {
                    if (dishIds.add(d.getId())) {
                        dishes.add(d);
                    }
                }
            }
        }

        if (status != null && !status.isBlank()) {
            dishes = dishes.stream()
                    .filter(d -> status.equals(d.getStatus()))
                    .toList();
        }

        return ApiResponse.success(dishes);
    }

    /**
     * 新增菜品。
     */
    @PostMapping
    public ApiResponse<Dish> create(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);

        Long merchantId = toLong(body.get("merchantId"));
        if (merchantId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "merchantId 不能为空");
        }
        verifyMerchantMembership(userId, merchantId);

        Dish dish = new Dish();
        dish.setMerchantId(merchantId);
        dish.setName(requireString(body, "name", "菜品名称不能为空"));
        dish.setPrice(parsePrice(body));
        dish.setCategory(getString(body, "category"));
        dish.setDescription(getString(body, "description"));
        dish.setImageUrl(getString(body, "imageUrl"));
        dish.setRecommended(Boolean.TRUE.equals(body.get("recommended")));

        dish.setTasteTags(parseTasteTags(body));

        String status = getString(body, "status");
        dish.setStatus(status != null && VALID_STATUSES.contains(status) ? status : "ACTIVE");

        dishMapper.insert(dish);

        log.info("商户 userId={} 新增菜品 dishId={}, name={}, merchantId={}",
                userId, dish.getId(), dish.getName(), merchantId);
        return ApiResponse.success("菜品新增成功", dish);
    }

    /**
     * 修改菜品。
     */
    @PutMapping("/{dishId}")
    public ApiResponse<Dish> update(
            @PathVariable Long dishId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);

        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            return ApiResponse.notFound("菜品不存在");
        }
        verifyMerchantMembership(userId, dish.getMerchantId());

        if (body.containsKey("name")) {
            String name = getString(body, "name");
            if (name == null || name.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "菜品名称不能为空");
            }
            dish.setName(name);
        }
        if (body.containsKey("price")) {
            dish.setPrice(parsePrice(body));
        }
        if (body.containsKey("category")) {
            dish.setCategory(getString(body, "category"));
        }
        if (body.containsKey("description")) {
            dish.setDescription(getString(body, "description"));
        }
        if (body.containsKey("imageUrl")) {
            dish.setImageUrl(getString(body, "imageUrl"));
        }
        if (body.containsKey("recommended")) {
            dish.setRecommended(Boolean.TRUE.equals(body.get("recommended")));
        }
        if (body.containsKey("tasteTags")) {
            dish.setTasteTags(parseTasteTags(body));
        }
        if (body.containsKey("status")) {
            String s = getString(body, "status");
            if (s != null && VALID_STATUSES.contains(s)) {
                dish.setStatus(s);
            }
        }

        dishMapper.updateById(dish);

        log.info("商户 userId={} 修改菜品 dishId={}", userId, dishId);
        return ApiResponse.success("菜品已更新", dish);
    }

    /**
     * 修改菜品状态（上下架/归档）。
     */
    @PutMapping("/{dishId}/status")
    public ApiResponse<Dish> updateStatus(
            @PathVariable Long dishId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);

        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            return ApiResponse.notFound("菜品不存在");
        }
        verifyMerchantMembership(userId, dish.getMerchantId());

        String newStatus = body.get("status");
        if (newStatus == null || !VALID_STATUSES.contains(newStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "状态值无效，可选值：ACTIVE/OFF_SHELF/ARCHIVED");
        }

        String oldStatus = dish.getStatus();
        dish.setStatus(newStatus);
        dishMapper.updateById(dish);

        // 记录状态变更历史
        if (!newStatus.equals(oldStatus)) {
            String reason = body.get("reason");
            contentStatusService.recordChange(
                    "DISH", dishId,
                    oldStatus, newStatus,
                    userId, reason
            );
        }

        dish.setStatus(newStatus);
        dishMapper.updateById(dish);

        log.info("商户 userId={} 修改菜品 dishId={} 状态为 {}", userId, dishId, newStatus);
        return ApiResponse.success("菜品状态已更新", dish);
    }

    /**
     * 下架菜品（软删除，status → OFF_SHELF）。
     */
    @DeleteMapping("/{dishId}")
    public ApiResponse<Void> delete(
            @PathVariable Long dishId,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);

        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            return ApiResponse.notFound("菜品不存在");
        }
        verifyMerchantMembership(userId, dish.getMerchantId());

        dish.setStatus("OFF_SHELF");
        dishMapper.updateById(dish);

        log.info("商户 userId={} 下架菜品 dishId={}", userId, dishId);
        return ApiResponse.success("菜品已下架", null);
    }

    // ============================================
    // 权限校验辅助方法
    // ============================================

    private Long requireUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
    }

    private void verifyMerchantMembership(Long userId, Long merchantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT 1 FROM merchant_members " +
                        "WHERE user_id = ? AND merchant_id = ? AND status = 'ACTIVE' " +
                        "LIMIT 1",
                userId, merchantId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "您不是该店铺的管理者，无权操作");
        }
    }

    private List<Long> getMyMerchantIds(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT merchant_id FROM merchant_members " +
                        "WHERE user_id = ? AND status = 'ACTIVE'",
                Long.class, userId
        );
    }

    private String parseTasteTags(Map<String, Object> body) {
        Object tags = body.get("tasteTags");
        if (tags instanceof List<?> list) {
            try {
                return objectMapper.writeValueAsString(list);
            } catch (JsonProcessingException e) {
                return "[]";
            }
        }
        if (tags instanceof String s && !s.isBlank()) {
            return s;
        }
        return "[]";
    }

    private String requireString(Map<String, Object> body, String key, String errorMsg) {
        Object val = body.get(key);
        if (val == null || String.valueOf(val).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", errorMsg);
        }
        return String.valueOf(val).trim();
    }

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val == null ? null : String.valueOf(val).trim();
    }

    private BigDecimal parsePrice(Map<String, Object> body) {
        Object val = body.get("price");
        if (val == null) return null;
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "价格格式不正确");
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number number) {
            return number.longValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
