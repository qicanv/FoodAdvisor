package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.RegionHotWordVO;
import com.foodadvisor.dto.RegionHotWordVO.HotWordMerchantBrief;
import com.foodadvisor.dto.RegionHotWordVO.RegionBriefVO;
import com.foodadvisor.service.RegionHotWordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 区域热词接口
 *
 * 提供区域热词的查询和生成触发功能。
 *
 * <h3>接口列表</h3>
 * <ul>
 *   <li>GET  /api/hot-words             — 分页查询热词列表</li>
 *   <li>GET  /api/hot-words/regions     — 获取有热词数据的区域列表</li>
 *   <li>GET  /api/hot-words/{id}/merchants — 获取热词关联商家</li>
 *   <li>POST /api/admin/hot-words/regenerate — 管理员手动触发热词生成</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/hot-words")
public class RegionHotWordController {

    private final RegionHotWordService hotWordService;

    public RegionHotWordController(RegionHotWordService hotWordService) {
        this.hotWordService = hotWordService;
    }

    /**
     * 分页查询热词列表（公开接口）。
     *
     * 前端调用此接口展示区域热词榜单，支持按区域、分类、周期筛选。
     *
     * 请求示例：
     *   GET /api/hot-words?regionCode=REGION-001&category=TASTE&periodType=WEEKLY&pageNum=1&pageSize=20
     *
     * @param regionCode 区域编码（可选，不传则查所有区域）
     * @param category   分类筛选（可选）：TASTE / SERVICE / ENVIRONMENT / PRICE / SPEED / GENERAL
     * @param periodType 周期筛选（可选）：DAILY / WEEKLY / MONTHLY，默认 WEEKLY
     * @param pageNum    页码，默认 1
     * @param pageSize   每页条数，默认 20
     * @return 分页热词结果（按热度降序）
     */
    @GetMapping
    public ApiResponse<PageResult<RegionHotWordVO>> list(
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String periodType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Page<RegionHotWordVO> page = hotWordService.queryHotWords(
                regionCode, category, periodType, pageNum, pageSize);
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 获取有热词数据的区域列表（公开接口）。
     *
     * 前端用此接口渲染区域选择器下拉列表。
     * 只有至少有一条热词数据的区域才会出现在列表中。
     *
     * 请求示例：
     *   GET /api/hot-words/regions
     *
     * @return 区域简要信息列表（含区域编码、热词总数、最热词预览）
     */
    @GetMapping("/regions")
    public ApiResponse<List<RegionBriefVO>> listRegions() {
        List<RegionBriefVO> regions = hotWordService.listRegionsWithHotWords();
        return ApiResponse.success(regions);
    }

    /**
     * 获取热词关联的商家列表（公开接口）。
     *
     * 前端点击某个热词后调用此接口，展示该热词在哪些商家评价中被提及。
     * 返回结果按提及次数降序排列。
     *
     * 请求示例：
     *   GET /api/hot-words/1/merchants?limit=10
     *
     * @param id    热词 ID
     * @param limit 最多返回商家数，默认 10
     * @return 关联商家简要信息列表
     */
    @GetMapping("/{id}/merchants")
    public ApiResponse<List<HotWordMerchantBrief>> getAssociatedMerchants(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<HotWordMerchantBrief> merchants = hotWordService.getAssociatedMerchants(id, limit);
        return ApiResponse.success(merchants);
    }

    // ==================== 管理接口 ====================

    /**
     * 管理员手动触发全量热词生成。
     *
     * 此接口用于运营人员在需要时即时刷新热词数据（如新增大量评价后）。
     * 可在请求体中指定周期类型和回溯天数。
     *
     * 请求示例：
     *   POST /api/admin/hot-words/regenerate
     *   Body: { "periodType": "WEEKLY", "daysBack": 7 }
     *
     * @param request 生成请求参数
     * @return 生成的热词总数
     */
    @PostMapping("/regenerate")
    public ApiResponse<RegenerateResponse> regenerate(
            @RequestBody RegenerateRequest request
    ) {
        String periodType = request.periodType != null ? request.periodType : "WEEKLY";
        int daysBack = request.daysBack > 0 ? request.daysBack : 7;

        int count = hotWordService.regenerateAll(periodType, daysBack);
        RegenerateResponse response = new RegenerateResponse();
        response.setGeneratedCount(count);
        response.setPeriodType(periodType);
        response.setDaysBack(daysBack);
        return ApiResponse.success("热词生成完成", response);
    }

    /**
     * 管理员手动触发单个区域的热词生成。
     *
     * 用于运营人员针对特定区域进行热词刷新。
     *
     * 请求示例：
     *   POST /api/admin/hot-words/regenerate/REGION-001
     *   Body: { "periodType": "WEEKLY", "daysBack": 7 }
     */
    @PostMapping("/regenerate/{regionCode}")
    public ApiResponse<RegenerateResponse> regenerateForRegion(
            @PathVariable String regionCode,
            @RequestBody RegenerateRequest request
    ) {
        String periodType = request.periodType != null ? request.periodType : "WEEKLY";
        int daysBack = request.daysBack > 0 ? request.daysBack : 7;

        java.time.LocalDate endDate = java.time.LocalDate.now();
        java.time.LocalDate startDate = endDate.minusDays(daysBack);

        int nextVersion = hotWordService.getNextVersionHelper(periodType, startDate, endDate);
        int count = hotWordService.generateForRegion(
                regionCode, periodType, startDate, endDate, nextVersion);

        RegenerateResponse response = new RegenerateResponse();
        response.setGeneratedCount(count);
        response.setPeriodType(periodType);
        response.setDaysBack(daysBack);
        response.setRegionCode(regionCode);
        return ApiResponse.success("区域热词生成完成", response);
    }

    // ==================== 请求/响应内部类 ====================

    /**
     * 热词生成请求参数
     */
    public static class RegenerateRequest {
        /** 周期类型：DAILY / WEEKLY / MONTHLY，默认 WEEKLY */
        public String periodType;

        /** 回溯天数（从今天往前推），默认 7 */
        public int daysBack;
    }

    /**
     * 热词生成响应
     */
    public static class RegenerateResponse {
        private int generatedCount;
        private String periodType;
        private int daysBack;
        private String regionCode;

        public int getGeneratedCount() { return generatedCount; }
        public void setGeneratedCount(int generatedCount) { this.generatedCount = generatedCount; }
        public String getPeriodType() { return periodType; }
        public void setPeriodType(String periodType) { this.periodType = periodType; }
        public int getDaysBack() { return daysBack; }
        public void setDaysBack(int daysBack) { this.daysBack = daysBack; }
        public String getRegionCode() { return regionCode; }
        public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    }
}
