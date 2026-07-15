package com.foodadvisor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.*;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.service.ReviewService;
import com.foodadvisor.mapper.MerchantMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantMapper merchantMapper;
    private final ReviewService reviewService;

    public MerchantController(MerchantMapper merchantMapper, ReviewService reviewService) {
        this.merchantMapper = merchantMapper;
        this.reviewService = reviewService;
    }

    /**
     * 商家列表 — 只返回正常营业的商家
     */
    @GetMapping
    public ApiResponse<PageResult<Merchant>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {

        Page<Merchant> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Merchant::getPlatformStatus, "ACTIVE")
               .eq(Merchant::getBusinessStatus, "OPEN");

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Merchant::getName, keyword)
                              .or().like(Merchant::getCategory, keyword));
        }
        wrapper.orderByDesc(Merchant::getRating);
        merchantMapper.selectPage(page, wrapper);
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 商家详情（含评价摘要）
     */
    @GetMapping("/{merchantId}")
    public ApiResponse<MerchantDetailVO> detail(@PathVariable Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return ApiResponse.notFound("商家不存在");
        }

        MerchantDetailVO vo = new MerchantDetailVO();
        vo.setMerchantId(merchant.getId());
        vo.setMerchantCode(merchant.getMerchantCode());
        vo.setMerchantName(merchant.getName());
        vo.setCategory(merchant.getCategory());
        vo.setCuisine(merchant.getCuisine());
        vo.setRating(merchant.getRating());
        vo.setAveragePrice(merchant.getAveragePrice());
        vo.setReviewCount(merchant.getReviewCount() != null
                ? Long.valueOf(merchant.getReviewCount()) : 0L);
        vo.setAddress(merchant.getAddress());
        vo.setRegionCode(merchant.getRegionCode());
        vo.setLongitude(merchant.getLongitude());
        vo.setLatitude(merchant.getLatitude());
        vo.setPhone(merchant.getPhone());
        vo.setDescription(merchant.getDescription());
        vo.setPlatformStatus(merchant.getPlatformStatus());
        vo.setBusinessStatus(merchant.getBusinessStatus());

        // 解析环境标签 JSONB
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            vo.setEnvironmentTags(mapper.readValue(
                    merchant.getEnvironmentTags(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {}));
        } catch (Exception e) {
            vo.setEnvironmentTags(new ArrayList<>());
        }

        // 营业时间先占位
        vo.setBusinessHours(new ArrayList<>());

        // 评价摘要
        long positiveCount = reviewService.countBySentiment(merchantId, "POSITIVE");
        long negativeCount = reviewService.countBySentiment(merchantId, "NEGATIVE");
        long total = positiveCount + negativeCount;

        ReviewSummaryVO summary = new ReviewSummaryVO();
        if (total > 0) {
            summary.setReviewCount((int) total);
        } else {
            summary.setReviewCount(0);
        }
        summary.setAdvantages(new ArrayList<>());
        summary.setDisadvantages(new ArrayList<>());
        vo.setReviewSummary(summary);

        return ApiResponse.success(vo);
    }
}
