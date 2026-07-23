package com.foodadvisor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.merchant.CreateMerchantRequest;
import com.foodadvisor.dto.merchant.OperationStatusRequest;
import com.foodadvisor.dto.merchant.UpdateMerchantRequest;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.MerchantBusinessHours;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.BusinessHoursMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.entity.OpenSearchSyncTask;
import com.foodadvisor.service.OpenSearchSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/merchant-console/merchants")
@Transactional
public class MerchantConsoleController {

    private static final Logger log =
            LoggerFactory.getLogger(MerchantConsoleController.class);

    private static final Set<String> VALID_OPERATION_STATUSES =
            Set.of("OPERATING", "SUSPENDED", "CLOSED_PERMANENTLY");

    private final MerchantMapper merchantMapper;
    private final BusinessHoursMapper businessHoursMapper;
    private final JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private OpenSearchSyncService openSearchSyncService;

    public MerchantConsoleController(
            MerchantMapper merchantMapper,
            BusinessHoursMapper businessHoursMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.merchantMapper = merchantMapper;
        this.businessHoursMapper = businessHoursMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ============================================
    // 店铺列表与详情
    // ============================================

    /**
     * 获取当前商户管理的店铺列表。
     */
    @GetMapping
    public ApiResponse<List<Merchant>> listMyMerchants(HttpServletRequest request) {
        Long userId = requireUserId(request);

        List<Long> merchantIds = jdbcTemplate.queryForList(
                "SELECT merchant_id FROM merchant_members " +
                        "WHERE user_id = ? AND status = 'ACTIVE' " +
                        "ORDER BY merchant_id",
                Long.class, userId
        );

        if (merchantIds.isEmpty()) {
            return ApiResponse.success(List.of());
        }

        List<Merchant> merchants = merchantMapper.selectBatchIds(merchantIds);
        return ApiResponse.success(merchants);
    }

    /**
     * 获取单个店铺详情。
     */
    @GetMapping("/{merchantId}")
    public ApiResponse<Merchant> getDetail(
            @PathVariable Long merchantId,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);
        verifyMerchantMembership(userId, merchantId);

        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return ApiResponse.notFound("店铺不存在");
        }
        return ApiResponse.success(merchant);
    }

    // ============================================
    // 店铺 CRUD
    // ============================================

    /**
     * 商户新建店铺，自动关联当前用户为 OWNER。
     */
    @PostMapping
    @Transactional
    public ApiResponse<Merchant> create(
            @RequestBody CreateMerchantRequest body,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);

        if (body.getName() == null || body.getName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "店名不能为空");
        }
        if (body.getCategory() == null || body.getCategory().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "商家类型不能为空");
        }
        if (body.getAddress() == null || body.getAddress().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "地址不能为空");
        }

        Merchant merchant = new Merchant();
        merchant.setMerchantCode(generateMerchantCode());
        merchant.setName(body.getName().trim());
        merchant.setCategory(body.getCategory().trim());
        merchant.setCuisine(body.getCuisine() != null ? body.getCuisine().trim() : null);
        merchant.setAveragePrice(body.getAveragePrice());
        merchant.setAddress(body.getAddress().trim());
        merchant.setRegionCode(body.getRegionCode() != null ? body.getRegionCode().trim() : null);
        merchant.setLongitude(body.getLongitude());
        merchant.setLatitude(body.getLatitude());
        merchant.setPhone(body.getPhone() != null ? body.getPhone().trim() : null);
        merchant.setDescription(body.getDescription() != null ? body.getDescription().trim() : null);
        merchant.setEnvironmentTags(
                body.getEnvironmentTags() != null && !body.getEnvironmentTags().isBlank()
                        ? body.getEnvironmentTags().trim()
                        : "[]"
        );
        merchant.setPlatformStatus("ACTIVE");
        merchant.setOperationStatus("OPERATING");
        merchant.setRating(BigDecimal.ZERO);
        merchant.setReviewCount(0);

        merchantMapper.insert(merchant);
        enqueueMerchantSync(merchant);

        jdbcTemplate.update(
                "INSERT INTO merchant_members (merchant_id, user_id, member_role, status, created_at, updated_at) " +
                        "VALUES (?, ?, 'OWNER', 'ACTIVE', NOW(), NOW())",
                merchant.getId(), userId
        );

        log.info("商户 userId={} 新建店铺 merchantId={}, name={}", userId, merchant.getId(), merchant.getName());
        return ApiResponse.success("店铺创建成功", merchant);
    }

    /**
     * 修改店铺基本信息（不可修改 merchantCode、platformStatus、rating、reviewCount）。
     */
    @PutMapping("/{merchantId}")
    public ApiResponse<Merchant> update(
            @PathVariable Long merchantId,
            @RequestBody UpdateMerchantRequest body,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);
        verifyMerchantMembership(userId, merchantId);

        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return ApiResponse.notFound("店铺不存在");
        }

        if (body.getName() != null && !body.getName().isBlank()) {
            merchant.setName(body.getName().trim());
        }
        if (body.getCategory() != null && !body.getCategory().isBlank()) {
            merchant.setCategory(body.getCategory().trim());
        }
        if (body.getCuisine() != null) {
            merchant.setCuisine(body.getCuisine().isBlank() ? null : body.getCuisine().trim());
        }
        if (body.getAveragePrice() != null) {
            merchant.setAveragePrice(body.getAveragePrice());
        }
        if (body.getAddress() != null && !body.getAddress().isBlank()) {
            merchant.setAddress(body.getAddress().trim());
        }
        if (body.getRegionCode() != null) {
            merchant.setRegionCode(body.getRegionCode().isBlank() ? null : body.getRegionCode().trim());
        }
        if (body.getLongitude() != null) {
            merchant.setLongitude(body.getLongitude());
        }
        if (body.getLatitude() != null) {
            merchant.setLatitude(body.getLatitude());
        }
        if (body.getPhone() != null) {
            merchant.setPhone(body.getPhone().isBlank() ? null : body.getPhone().trim());
        }
        if (body.getDescription() != null) {
            merchant.setDescription(body.getDescription().isBlank() ? null : body.getDescription().trim());
        }
        if (body.getEnvironmentTags() != null) {
            merchant.setEnvironmentTags(
                    body.getEnvironmentTags().isBlank() ? "[]" : body.getEnvironmentTags().trim()
            );
        }

        merchantMapper.updateById(merchant);
        enqueueMerchantSync(merchant);

        log.info("商户 userId={} 修改店铺 merchantId={}", userId, merchantId);
        return ApiResponse.success("店铺信息已更新", merchant);
    }

    // ============================================
    // 经营状态管理
    // ============================================

    /**
     * 修改店铺经营状态。商户只能改 operationStatus，不能改 platformStatus。
     */
    @PutMapping("/{merchantId}/operation-status")
    public ApiResponse<Merchant> updateOperationStatus(
            @PathVariable Long merchantId,
            @RequestBody OperationStatusRequest body,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);
        verifyMerchantMembership(userId, merchantId);

        if (body.getOperationStatus() == null
                || !VALID_OPERATION_STATUSES.contains(body.getOperationStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "经营状态值无效，可选值：OPERATING/SUSPENDED/CLOSED_PERMANENTLY");
        }

        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return ApiResponse.notFound("店铺不存在");
        }

        String oldStatus = merchant.getOperationStatus();
        merchant.setOperationStatus(body.getOperationStatus());
        merchantMapper.updateById(merchant);
        enqueueMerchantSync(merchant);

        log.info("商户 userId={} 修改店铺 merchantId={} 经营状态: {} -> {}",
                userId, merchantId, oldStatus, body.getOperationStatus());
        return ApiResponse.success("经营状态已更新", merchant);
    }

    // ============================================
    // 营业时间管理
    // ============================================

    /**
     * 获取店铺营业时间。
     */
    @GetMapping("/{merchantId}/business-hours")
    public ApiResponse<List<MerchantBusinessHours>> getBusinessHours(
            @PathVariable Long merchantId,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);
        verifyMerchantMembership(userId, merchantId);

        List<MerchantBusinessHours> hours = businessHoursMapper.selectByMerchantId(merchantId);
        return ApiResponse.success(hours);
    }

    /**
     * 修改店铺营业时间。全量替换。
     */
    @PutMapping("/{merchantId}/business-hours")
    @Transactional
    public ApiResponse<List<MerchantBusinessHours>> updateBusinessHours(
            @PathVariable Long merchantId,
            @RequestBody List<MerchantBusinessHours> businessHours,
            HttpServletRequest request
    ) {
        Long userId = requireUserId(request);
        verifyMerchantMembership(userId, merchantId);

        jdbcTemplate.update(
                "DELETE FROM merchant_business_hours WHERE merchant_id = ?", merchantId
        );

        for (MerchantBusinessHours hour : businessHours) {
            hour.setId(null);
            hour.setMerchantId(merchantId);
            businessHoursMapper.insert(hour);
        }
        enqueueMerchantSync(merchantMapper.selectById(merchantId));

        log.info("商户 userId={} 修改店铺 merchantId={} 营业时间", userId, merchantId);
        List<MerchantBusinessHours> updated = businessHoursMapper.selectByMerchantId(merchantId);
        return ApiResponse.success("营业时间已更新", updated);
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

    private void enqueueMerchantSync(Merchant merchant) {
        if (openSearchSyncService == null || merchant == null || merchant.getId() == null) {
            return;
        }
        boolean searchable = "ACTIVE".equals(merchant.getPlatformStatus())
                && "OPERATING".equals(merchant.getOperationStatus())
                && merchant.getDeletedAt() == null;
        openSearchSyncService.createSyncTask(
                "MERCHANT",
                merchant.getId(),
                searchable ? OpenSearchSyncTask.OP_UPSERT
                        : OpenSearchSyncTask.OP_DISABLE);
    }

    private String generateMerchantCode() {
        List<String> codes = jdbcTemplate.queryForList(
                "SELECT merchant_code FROM merchants " +
                        "WHERE merchant_code LIKE 'M-%' " +
                        "ORDER BY merchant_code DESC LIMIT 1",
                String.class
        );
        if (codes.isEmpty()) {
            return "M-000001";
        }
        String lastCode = codes.get(0);
        try {
            int num = Integer.parseInt(lastCode.substring(2)) + 1;
            return String.format("M-%06d", num);
        } catch (NumberFormatException e) {
            return "M-" + System.currentTimeMillis();
        }
    }
}
