package com.foodadvisor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.backend.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.mapper.MerchantMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/merchants")
public class AdminMerchantController {

    private final MerchantMapper merchantMapper;

    public AdminMerchantController(MerchantMapper merchantMapper) {
        this.merchantMapper = merchantMapper;
    }

    private boolean isAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        return "ADMIN".equals(role);
    }

    @GetMapping
    public ApiResponse<PageResult<Merchant>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String platformStatus,
            @RequestParam(required = false) String operationStatus,
            HttpServletRequest request
    ) {
        if (!isAdmin(request)) {
            return ApiResponse.failure("FORBIDDEN", "无管理权限");
        }

        Page<Merchant> page = Page.of(pageNum, pageSize);

        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(query ->
                    query.like(Merchant::getName, keyword)
                            .or()
                            .like(Merchant::getMerchantCode, keyword)
                            .or()
                            .like(Merchant::getCategory, keyword)
                            .or()
                            .like(Merchant::getAddress, keyword)
            );
        }

        if (platformStatus != null && !platformStatus.isBlank()) {
            wrapper.eq(Merchant::getPlatformStatus, platformStatus);
        }

        if (operationStatus != null && !operationStatus.isBlank()) {
            wrapper.eq(Merchant::getOperationStatus, operationStatus);
        }

        wrapper.orderByDesc(Merchant::getUpdatedAt);
        merchantMapper.selectPage(page, wrapper);

        return ApiResponse.success(PageResult.from(page));
    }

    @GetMapping("/{id}")
    public ApiResponse<Merchant> getById(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        if (!isAdmin(request)) {
            return ApiResponse.failure("FORBIDDEN", "无管理权限");
        }

        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            return ApiResponse.notFound("商家不存在");
        }

        return ApiResponse.success(merchant);
    }

    @PostMapping
    public ApiResponse<Merchant> create(
            @RequestBody Merchant merchant,
            HttpServletRequest request
    ) {
        if (!isAdmin(request)) {
            return ApiResponse.failure("FORBIDDEN", "无管理权限");
        }

        List<String> errors = validateMerchant(merchant);
        if (!errors.isEmpty()) {
            return ApiResponse.failure("VALIDATION_ERROR", String.join("; ", errors));
        }

        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Merchant::getMerchantCode, merchant.getMerchantCode());
        if (merchantMapper.selectCount(wrapper) > 0) {
            return ApiResponse.failure("DUPLICATE_CODE", "商家编号已存在");
        }

        if (merchant.getPlatformStatus() == null || merchant.getPlatformStatus().isBlank()) {
            merchant.setPlatformStatus("ACTIVE");
        }
        if (merchant.getOperationStatus() == null || merchant.getOperationStatus().isBlank()) {
            merchant.setOperationStatus("OPERATING");
        }
        if (merchant.getRating() == null) {
            merchant.setRating(BigDecimal.ZERO);
        }
        if (merchant.getReviewCount() == null) {
            merchant.setReviewCount(0);
        }

        merchantMapper.insert(merchant);

        return ApiResponse.success(merchant);
    }

    @PutMapping("/{id}")
    public ApiResponse<Merchant> update(
            @PathVariable Long id,
            @RequestBody Merchant merchant,
            HttpServletRequest request
    ) {
        if (!isAdmin(request)) {
            return ApiResponse.failure("FORBIDDEN", "无管理权限");
        }

        Merchant existing = merchantMapper.selectById(id);
        if (existing == null) {
            return ApiResponse.notFound("商家不存在");
        }

        List<String> errors = validateMerchant(merchant);
        if (!errors.isEmpty()) {
            return ApiResponse.failure("VALIDATION_ERROR", String.join("; ", errors));
        }

        if (!existing.getMerchantCode().equals(merchant.getMerchantCode())) {
            LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Merchant::getMerchantCode, merchant.getMerchantCode());
            if (merchantMapper.selectCount(wrapper) > 0) {
                return ApiResponse.failure("DUPLICATE_CODE", "商家编号已存在");
            }
        }

        merchant.setId(id);
        merchant.setCreatedAt(existing.getCreatedAt());

        merchantMapper.updateById(merchant);

        return ApiResponse.success(merchantMapper.selectById(id));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Merchant> updateStatus(
            @PathVariable Long id,
            @RequestParam String platformStatus,
            @RequestParam(required = false) String operationStatus,
            HttpServletRequest request
    ) {
        if (!isAdmin(request)) {
            return ApiResponse.failure("FORBIDDEN", "无管理权限");
        }

        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            return ApiResponse.notFound("商家不存在");
        }

        if (platformStatus != null && !platformStatus.isBlank()) {
            merchant.setPlatformStatus(platformStatus);
        }
        if (operationStatus != null && !operationStatus.isBlank()) {
            merchant.setOperationStatus(operationStatus);
        }

        merchantMapper.updateById(merchant);

        return ApiResponse.success(merchant);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        if (!isAdmin(request)) {
            return ApiResponse.failure("FORBIDDEN", "无管理权限");
        }

        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            return ApiResponse.notFound("商家不存在");
        }

        merchant.setPlatformStatus("DISABLED");
        merchantMapper.updateById(merchant);

        return ApiResponse.success(null);
    }

    private List<String> validateMerchant(Merchant merchant) {
        List<String> errors = new ArrayList<>();

        if (merchant.getMerchantCode() == null || merchant.getMerchantCode().isBlank()) {
            errors.add("商家编号不能为空");
        }

        if (merchant.getName() == null || merchant.getName().isBlank()) {
            errors.add("店名不能为空");
        }

        if (merchant.getAddress() == null || merchant.getAddress().isBlank()) {
            errors.add("地址不能为空");
        }

        if (merchant.getCategory() == null || merchant.getCategory().isBlank()) {
            errors.add("商家类型不能为空");
        }

        if (merchant.getPlatformStatus() == null || merchant.getPlatformStatus().isBlank()) {
            errors.add("平台状态不能为空");
        } else if (!isValidPlatformStatus(merchant.getPlatformStatus())) {
            errors.add("平台状态值无效，可选值：ACTIVE/DISABLED/ARCHIVED");
        }

        if (merchant.getOperationStatus() == null || merchant.getOperationStatus().isBlank()) {
            errors.add("营业状态不能为空");
        } else if (!isValidOperationStatus(merchant.getOperationStatus())) {
            errors.add("营业状态值无效，可选值：OPERATING/SUSPENDED/CLOSED_PERMANENTLY");
        }

        if (merchant.getPhone() != null && !merchant.getPhone().isBlank()) {
            if (!merchant.getPhone().matches("^1[3-9]\\d{9}$")) {
                errors.add("联系电话格式不正确");
            }
        }

        if (merchant.getAveragePrice() != null && merchant.getAveragePrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("人均消费不能为负数");
        }

        if (merchant.getRating() != null && (merchant.getRating().compareTo(BigDecimal.ZERO) < 0 || merchant.getRating().compareTo(new BigDecimal("5")) > 0)) {
            errors.add("评分必须在0-5之间");
        }

        if (merchant.getLongitude() != null && (merchant.getLongitude().compareTo(new BigDecimal("-180")) < 0 || merchant.getLongitude().compareTo(new BigDecimal("180")) > 0)) {
            errors.add("经度必须在-180到180之间");
        }

        if (merchant.getLatitude() != null && (merchant.getLatitude().compareTo(new BigDecimal("-90")) < 0 || merchant.getLatitude().compareTo(new BigDecimal("90")) > 0)) {
            errors.add("纬度必须在-90到90之间");
        }

        return errors;
    }

    private boolean isValidPlatformStatus(String status) {
        return "ACTIVE".equals(status) || "DISABLED".equals(status) || "ARCHIVED".equals(status);
    }

    private boolean isValidOperationStatus(String status) {
        return "OPERATING".equals(status) || "SUSPENDED".equals(status) || "CLOSED_PERMANENTLY".equals(status);
    }
}
