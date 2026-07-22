package com.foodadvisor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.entity.ContentStatusHistory;
import com.foodadvisor.entity.Dish;
import com.foodadvisor.entity.OpenSearchSyncTask;
import com.foodadvisor.mapper.DishMapper;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.ContentStatusService;
import com.foodadvisor.service.OpenSearchSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/admin/dishes")
public class AdminDishController {

    private static final Logger log =
            LoggerFactory.getLogger(AdminDishController.class);

    private static final String CONTENT_TYPE_DISH = "DISH";
    private static final Set<String> VALID_STATUSES =
            Set.of("ACTIVE", "OFF_SHELF", "ARCHIVED");

    private final DishMapper dishMapper;
    private final AdminAccessGuard adminAccessGuard;
    private final ContentStatusService contentStatusService;
    private final OpenSearchSyncService openSearchSyncService;

    public AdminDishController(
            DishMapper dishMapper,
            AdminAccessGuard adminAccessGuard,
            ContentStatusService contentStatusService,
            OpenSearchSyncService openSearchSyncService
    ) {
        this.dishMapper = dishMapper;
        this.adminAccessGuard = adminAccessGuard;
        this.contentStatusService = contentStatusService;
        this.openSearchSyncService = openSearchSyncService;
    }

    /**
     * 分页查询所有菜品（管理员视角），支持筛选。
     */
    @GetMapping
    public ApiResponse<PageResult<Dish>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long merchantId,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Page<Dish> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(query ->
                    query.like(Dish::getName, keyword)
                            .or()
                            .like(Dish::getCategory, keyword)
                            .or()
                            .like(Dish::getDescription, keyword)
            );
        }

        if (status != null && !status.isBlank()) {
            wrapper.eq(Dish::getStatus, status);
        }

        if (merchantId != null) {
            wrapper.eq(Dish::getMerchantId, merchantId);
        }

        wrapper.orderByDesc(Dish::getUpdatedAt);
        dishMapper.selectPage(page, wrapper);

        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 获取菜品详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<Dish> getById(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            return ApiResponse.notFound("菜品不存在");
        }

        return ApiResponse.success(dish);
    }

    /**
     * 新增菜品。
     */
    @PostMapping
    public ApiResponse<Dish> create(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Long merchantId = toLong(body.get("merchantId"));
        if (merchantId == null) {
            return ApiResponse.failure("VALIDATION_ERROR", "merchantId 不能为空");
        }

        String name = getString(body, "name");
        if (name == null || name.isBlank()) {
            return ApiResponse.failure("VALIDATION_ERROR", "菜品名称不能为空");
        }

        Dish dish = new Dish();
        dish.setMerchantId(merchantId);
        dish.setName(name.trim());
        dish.setPrice(parsePrice(body));
        dish.setCategory(getString(body, "category"));
        dish.setDescription(getString(body, "description"));
        dish.setImageUrl(getString(body, "imageUrl"));
        dish.setRecommended(Boolean.TRUE.equals(body.get("recommended")));
        dish.setTasteTags(parseTasteTags(body));

        String status = getString(body, "status");
        dish.setStatus(status != null && VALID_STATUSES.contains(status) ? status : "ACTIVE");

        dishMapper.insert(dish);

        // 记录初始状态
        Long userId = getUserId(request);
        contentStatusService.recordChange(
                CONTENT_TYPE_DISH, dish.getId(),
                null, dish.getStatus(),
                userId, "管理员新增菜品"
        );

        log.info("管理员 userId={} 新增菜品 dishId={}, name={}, merchantId={}",
                userId, dish.getId(), dish.getName(), merchantId);
        return ApiResponse.success("菜品新增成功", dish);
    }

    /**
     * 修改菜品信息。
     */
    @PutMapping("/{id}")
    public ApiResponse<Dish> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            return ApiResponse.notFound("菜品不存在");
        }

        String oldStatus = dish.getStatus();

        if (body.containsKey("name")) {
            String name = getString(body, "name");
            if (name == null || name.isBlank()) {
                return ApiResponse.failure("VALIDATION_ERROR", "菜品名称不能为空");
            }
            dish.setName(name.trim());
        }
        if (body.containsKey("merchantId")) {
            Long merchantId = toLong(body.get("merchantId"));
            if (merchantId != null) {
                dish.setMerchantId(merchantId);
            }
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
            String newStatus = getString(body, "status");
            if (newStatus != null && VALID_STATUSES.contains(newStatus)) {
                dish.setStatus(newStatus);
            }
        }

        dishMapper.updateById(dish);

        // 状态变更时记录历史并触发同步
        String newStatus = dish.getStatus();
        if (!Objects.equals(oldStatus, newStatus)) {
            Long userId = getUserId(request);
            String reason = getString(body, "reason");
            contentStatusService.recordChange(
                    CONTENT_TYPE_DISH, id,
                    oldStatus, newStatus,
                    userId, reason
            );
            triggerSync(id, newStatus);
        }

        log.info("管理员 userId={} 修改菜品 dishId={}", getUserId(request), id);
        return ApiResponse.success("菜品已更新", dish);
    }

    /**
     * 修改菜品状态。
     * 请求体：{ status, reason }
     */
    @PutMapping("/{id}/status")
    public ApiResponse<Dish> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            return ApiResponse.notFound("菜品不存在");
        }

        String oldStatus = dish.getStatus();
        String newStatus = body.get("status");
        String reason = body.get("reason");

        if (newStatus == null || !VALID_STATUSES.contains(newStatus)) {
            return ApiResponse.failure("VALIDATION_ERROR",
                    "状态值无效，可选值：ACTIVE/OFF_SHELF/ARCHIVED");
        }

        if (newStatus.equals(oldStatus)) {
            return ApiResponse.success("状态未变化", dish);
        }

        dish.setStatus(newStatus);
        dishMapper.updateById(dish);

        // 记录历史
        Long userId = getUserId(request);
        contentStatusService.recordChange(
                CONTENT_TYPE_DISH, id,
                oldStatus, newStatus,
                userId, reason
        );

        // 触发 OpenSearch 同步
        triggerSync(id, newStatus);

        log.info("管理员 userId={} 修改菜品 dishId={} 状态: {} -> {}", userId, id, oldStatus, newStatus);
        return ApiResponse.success("菜品状态已更新", dish);
    }

    /**
     * 恢复菜品（OFF_SHELF/ARCHIVED → ACTIVE）。
     * 请求体：{ reason }
     */
    @PutMapping("/{id}/restore")
    public ApiResponse<Dish> restore(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            return ApiResponse.notFound("菜品不存在");
        }

        String oldStatus = dish.getStatus();
        String reason = body != null ? body.get("reason") : null;

        if ("ACTIVE".equals(oldStatus)) {
            return ApiResponse.failure("INVALID_STATUS", "该菜品当前为上架状态，无需恢复");
        }

        dish.setStatus("ACTIVE");
        dishMapper.updateById(dish);

        // 记录历史
        Long userId = getUserId(request);
        contentStatusService.recordChange(
                CONTENT_TYPE_DISH, id,
                oldStatus, "ACTIVE",
                userId, reason != null ? reason : "恢复菜品"
        );

        // 触发同步
        triggerSync(id, "ACTIVE");

        log.info("菜品已恢复: dishId={}, {} -> ACTIVE, operator={}", id, oldStatus, userId);
        return ApiResponse.success("菜品已恢复", dish);
    }

    /**
     * 查询菜品状态变更历史。
     */
    @GetMapping("/{id}/status-history")
    public ApiResponse<List<ContentStatusHistory>> getStatusHistory(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            return ApiResponse.notFound("菜品不存在");
        }

        List<ContentStatusHistory> history =
                contentStatusService.getHistory(CONTENT_TYPE_DISH, id);

        return ApiResponse.success(history);
    }

    /**
     * 下架菜品（软删除，status → OFF_SHELF）。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            return ApiResponse.notFound("菜品不存在");
        }

        String oldStatus = dish.getStatus();
        dish.setStatus("OFF_SHELF");
        dishMapper.updateById(dish);

        // 记录历史
        Long userId = getUserId(request);
        contentStatusService.recordChange(
                CONTENT_TYPE_DISH, id,
                oldStatus, "OFF_SHELF",
                userId, "管理员下架"
        );

        // 触发同步
        triggerSync(id, "OFF_SHELF");

        log.info("管理员 userId={} 下架菜品 dishId={}", userId, id);
        return ApiResponse.success("菜品已下架", null);
    }

    // ============================================
    // 辅助方法
    // ============================================

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private void triggerSync(Long dishId, String newStatus) {
        try {
            if ("OFF_SHELF".equals(newStatus) || "ARCHIVED".equals(newStatus)) {
                openSearchSyncService.createSyncTask(
                        CONTENT_TYPE_DISH, dishId,
                        OpenSearchSyncTask.OP_DISABLE
                );
            } else if ("ACTIVE".equals(newStatus)) {
                openSearchSyncService.createSyncTask(
                        CONTENT_TYPE_DISH, dishId,
                        OpenSearchSyncTask.OP_UPSERT
                );
            }
        } catch (Exception e) {
            log.error("创建同步任务失败: dishId={}, newStatus={}, error={}",
                    dishId, newStatus, e.getMessage(), e);
        }
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
            return null;
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number number) {
            return number.longValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String parseTasteTags(Map<String, Object> body) {
        Object tags = body.get("tasteTags");
        if (tags instanceof List<?> list) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list);
            } catch (Exception e) {
                return "[]";
            }
        }
        if (tags instanceof String s && !s.isBlank()) {
            return s;
        }
        return "[]";
    }
}
