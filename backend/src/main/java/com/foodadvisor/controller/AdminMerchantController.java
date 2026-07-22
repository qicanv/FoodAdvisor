package com.foodadvisor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.entity.ContentStatusHistory;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.ContentStatusService;
import com.foodadvisor.service.KnowledgeEnrichmentService;
import com.foodadvisor.service.OpenSearchSyncService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin/merchants")
@Slf4j
public class AdminMerchantController {

    private static final String CONTENT_TYPE_MERCHANT = "MERCHANT";

    private final MerchantMapper merchantMapper;
    private final AdminAccessGuard adminAccessGuard;
    private final ContentStatusService contentStatusService;
    private final OpenSearchSyncService openSearchSyncService;
    private final KnowledgeEnrichmentService knowledgeEnrichmentService;

    public AdminMerchantController(
            MerchantMapper merchantMapper,
            AdminAccessGuard adminAccessGuard,
            ContentStatusService contentStatusService,
            OpenSearchSyncService openSearchSyncService,
            KnowledgeEnrichmentService knowledgeEnrichmentService
    ) {
        this.merchantMapper = merchantMapper;
        this.adminAccessGuard = adminAccessGuard;
        this.contentStatusService = contentStatusService;
        this.openSearchSyncService = openSearchSyncService;
        this.knowledgeEnrichmentService = knowledgeEnrichmentService;
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
        adminAccessGuard.requireAdmin(request);

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

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);

        Map<String, Object> stats = new HashMap<>();

        long total = merchantMapper.selectCount(null);
        log.info("商家统计 - 总商家数: {}", total);

        long activeCount = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getPlatformStatus, "ACTIVE")
                        .eq(Merchant::getOperationStatus, "OPERATING")
        );
        log.info("商家统计 - 正常营业: {}", activeCount);

        long disabledCount = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getPlatformStatus, "DISABLED")
        );
        log.info("商家统计 - 已禁用: {}", disabledCount);

        long suspendedCount = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>()
                        .in(Merchant::getOperationStatus, "SUSPENDED", "CLOSED_PERMANENTLY")
        );
        log.info("商家统计 - 停业中: {}", suspendedCount);

        long archivedCount = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getPlatformStatus, "ARCHIVED")
        );
        log.info("商家统计 - 已归档: {}", archivedCount);

        stats.put("total", total);
        stats.put("activeCount", activeCount);
        stats.put("disabledCount", disabledCount);
        stats.put("suspendedCount", suspendedCount);
        stats.put("archivedCount", archivedCount);

        return ApiResponse.success(stats);
    }

    @GetMapping("/{id}")
    public ApiResponse<Merchant> getById(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

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
        adminAccessGuard.requireAdmin(request);

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

        // 记录初始状态
        Long userId = getUserId(request);
        contentStatusService.recordChange(
                CONTENT_TYPE_MERCHANT, merchant.getId(),
                null, merchant.getPlatformStatus(),
                userId, "商家创建"
        );

        // 异步触发知识入库（增强文本 → OpenSearch）
        if ("ACTIVE".equals(merchant.getPlatformStatus())) {
            triggerKnowledgeSync(merchant.getId());
        }

        return ApiResponse.success(merchant);
    }

    @PutMapping("/{id}")
    public ApiResponse<Merchant> update(
            @PathVariable Long id,
            @RequestBody Merchant merchant,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

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

        // 检测平台状态是否发生变化
        String oldPlatformStatus = existing.getPlatformStatus();
        String newPlatformStatus = merchant.getPlatformStatus();

        merchant.setId(id);
        merchant.setCreatedAt(existing.getCreatedAt());

        // 平台状态变更时更新 status_changed_at
        if (newPlatformStatus != null && !newPlatformStatus.equals(oldPlatformStatus)) {
            merchant.setStatusChangedAt(OffsetDateTime.now());
        }

        merchantMapper.updateById(merchant);

        // 记录状态变更历史并触发同步
        if (newPlatformStatus != null && !newPlatformStatus.equals(oldPlatformStatus)) {
            Long userId = getUserId(request);
            contentStatusService.recordChange(
                    CONTENT_TYPE_MERCHANT, id,
                    oldPlatformStatus, newPlatformStatus,
                    userId, "编辑商家时修改状态"
            );
            triggerSync(id, newPlatformStatus);
        }

        return ApiResponse.success(merchantMapper.selectById(id));
    }

    /**
     * 修改商家平台状态和营业状态。
     * 请求体：{ platformStatus, operationStatus, reason }
     */
    @PutMapping("/{id}/status")
    public ApiResponse<Merchant> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            return ApiResponse.notFound("商家不存在");
        }

        String oldPlatformStatus = merchant.getPlatformStatus();
        String oldOperationStatus = merchant.getOperationStatus();

        String newPlatformStatus = body.get("platformStatus");
        String newOperationStatus = body.get("operationStatus");
        String reason = body.get("reason");

        boolean statusChanged = false;

        if (newPlatformStatus != null && !newPlatformStatus.isBlank()
                && isValidPlatformStatus(newPlatformStatus)) {
            if (!newPlatformStatus.equals(oldPlatformStatus)) {
                merchant.setPlatformStatus(newPlatformStatus);
                statusChanged = true;
            }
        }

        if (newOperationStatus != null && !newOperationStatus.isBlank()
                && isValidOperationStatus(newOperationStatus)) {
            if (!newOperationStatus.equals(oldOperationStatus)) {
                merchant.setOperationStatus(newOperationStatus);
                statusChanged = true;
            }
        }

        if (statusChanged) {
            merchant.setStatusChangedAt(OffsetDateTime.now());
        }

        merchantMapper.updateById(merchant);

        // 记录状态变更历史
        if (statusChanged) {
            Long userId = getUserId(request);

            // 分别记录平台状态和营业状态的变化
            if (newPlatformStatus != null && !newPlatformStatus.equals(oldPlatformStatus)) {
                contentStatusService.recordChange(
                        CONTENT_TYPE_MERCHANT, id,
                        oldPlatformStatus, newPlatformStatus,
                        userId, reason
                );
                triggerSync(id, newPlatformStatus);
            }

            if (newOperationStatus != null && !newOperationStatus.equals(oldOperationStatus)) {
                contentStatusService.recordChange(
                        CONTENT_TYPE_MERCHANT, id,
                        oldOperationStatus, newOperationStatus,
                        userId, reason
                );
            }
        }

        return ApiResponse.success(merchant);
    }

    /**
     * 恢复已停用/归档的商家。
     * 请求体：{ reason }
     */
    @PutMapping("/{id}/restore")
    public ApiResponse<Merchant> restore(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            return ApiResponse.notFound("商家不存在");
        }

        String oldStatus = merchant.getPlatformStatus();
        String reason = body != null ? body.get("reason") : null;

        if ("ACTIVE".equals(oldStatus)) {
            return ApiResponse.failure("INVALID_STATUS", "该商家当前为正常状态，无需恢复");
        }

        // 恢复为正常状态
        merchant.setPlatformStatus("ACTIVE");
        merchant.setStatusChangedAt(OffsetDateTime.now());
        merchantMapper.updateById(merchant);

        // 记录历史
        Long userId = getUserId(request);
        contentStatusService.recordChange(
                CONTENT_TYPE_MERCHANT, id,
                oldStatus, "ACTIVE",
                userId, reason != null ? reason : "恢复商家"
        );

        // 触发同步（恢复为启用状态）
        triggerSync(id, "ACTIVE");

        log.info("商家已恢复: merchantId={}, {} -> ACTIVE, operator={}", id, oldStatus, userId);
        return ApiResponse.success(merchant);
    }

    /**
     * 查询商家状态变更历史。
     */
    @GetMapping("/{id}/status-history")
    public ApiResponse<List<ContentStatusHistory>> getStatusHistory(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            return ApiResponse.notFound("商家不存在");
        }

        List<ContentStatusHistory> history =
                contentStatusService.getHistory(CONTENT_TYPE_MERCHANT, id);

        return ApiResponse.success(history);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            return ApiResponse.notFound("商家不存在");
        }

        String oldStatus = merchant.getPlatformStatus();
        merchant.setPlatformStatus("DISABLED");
        merchant.setStatusChangedAt(OffsetDateTime.now());
        merchantMapper.updateById(merchant);

        // 记录历史
        Long userId = getUserId(request);
        contentStatusService.recordChange(
                CONTENT_TYPE_MERCHANT, id,
                oldStatus, "DISABLED",
                userId, "管理员删除（软删除）"
        );

        // 触发 OpenSearch 同步
        triggerSync(id, "DISABLED");

        return ApiResponse.success(null);
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

    private void triggerSync(Long merchantId, String newStatus) {
        try {
            if ("DISABLED".equals(newStatus) || "ARCHIVED".equals(newStatus)) {
                openSearchSyncService.createSyncTask(
                        CONTENT_TYPE_MERCHANT, merchantId,
                        com.foodadvisor.entity.OpenSearchSyncTask.OP_DISABLE
                );
            } else if ("ACTIVE".equals(newStatus)) {
                openSearchSyncService.createSyncTask(
                        CONTENT_TYPE_MERCHANT, merchantId,
                        com.foodadvisor.entity.OpenSearchSyncTask.OP_UPSERT
                );
            }
        } catch (Exception e) {
            log.error("创建同步任务失败: merchantId={}, newStatus={}, error={}",
                    merchantId, newStatus, e.getMessage(), e);
        }
    }

    /**
     * 立即触发商家知识同步（增强文本 → 清洗切分 → 向量化 → OpenSearch）。
     * 耗时较长（需调用 Embedding 模型），适合在后台线程中执行。
     */
    private void triggerKnowledgeSync(Long merchantId) {
        new Thread(() -> {
            try {
                Map<String, Integer> result =
                        knowledgeEnrichmentService.syncMerchantAll(merchantId);
                log.info("商家知识同步完成: merchantId={}, result={}", merchantId, result);
            } catch (Exception e) {
                log.error("商家知识同步失败: merchantId={}, error={}",
                        merchantId, e.getMessage(), e);
            }
        }, "knowledge-sync-" + merchantId).start();
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
