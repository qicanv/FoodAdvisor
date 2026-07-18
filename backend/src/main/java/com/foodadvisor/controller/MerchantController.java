package com.foodadvisor.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.BusinessHourVO;
import com.foodadvisor.dto.MerchantDetailVO;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.ReviewSummaryVO;
import com.foodadvisor.dto.review.MerchantRatingSummaryVO;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.MerchantBusinessHours;
import com.foodadvisor.mapper.BusinessHoursMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantMapper merchantMapper;
    private final ReviewService reviewService;
    private final ObjectMapper objectMapper;
    private final BusinessHoursMapper businessHoursMapper;

    public MerchantController(
            MerchantMapper merchantMapper,
            ReviewService reviewService,
            ObjectMapper objectMapper,
            BusinessHoursMapper businessHoursMapper
    ) {
        this.merchantMapper = merchantMapper;
        this.reviewService = reviewService;
        this.objectMapper = objectMapper;
        this.businessHoursMapper = businessHoursMapper;
    }

    /**
     * 商家列表——只返回平台启用且正常营业的商家。
     */
    @GetMapping
    public ApiResponse<PageResult<Merchant>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword
    ) {
        Page<Merchant> page = Page.of(pageNum, pageSize);

        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Merchant::getPlatformStatus, "ACTIVE")
                .eq(Merchant::getOperationStatus, "OPERATING");

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(query ->
                    query.like(Merchant::getName, keyword)
                            .or()
                            .like(Merchant::getCategory, keyword)
            );
        }

        wrapper.orderByDesc(Merchant::getRating);
        merchantMapper.selectPage(page, wrapper);

        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 商家详情，包含评价评分摘要。
     * 仅返回平台启用且正常营业的商家详情。
     */
    @GetMapping("/{merchantId}")
    public ApiResponse<MerchantDetailVO> detail(
            @PathVariable Long merchantId
    ) {
        Merchant merchant = merchantMapper.selectById(merchantId);

        if (merchant == null) {
            return ApiResponse.notFound("商家不存在");
        }

        if (!"ACTIVE".equals(merchant.getPlatformStatus()) || !"OPERATING".equals(merchant.getOperationStatus())) {
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
        vo.setReviewCount(
                merchant.getReviewCount() != null
                        ? Long.valueOf(merchant.getReviewCount())
                        : 0L
        );
        vo.setAddress(merchant.getAddress());
        vo.setRegionCode(merchant.getRegionCode());
        vo.setLongitude(merchant.getLongitude());
        vo.setLatitude(merchant.getLatitude());
        vo.setPhone(merchant.getPhone());
        vo.setDescription(merchant.getDescription());
        vo.setPlatformStatus(merchant.getPlatformStatus());
        vo.setBusinessStatus(merchant.getOperationStatus());

        MerchantRatingSummaryVO ratingSummary =
                reviewService.calculateMerchantRatingSummary(merchantId);

        vo.setAverageRating(ratingSummary.getAverageRating());
        vo.setAverageTasteRating(
                ratingSummary.getAverageTasteRating()
        );
        vo.setAverageEnvironmentRating(
                ratingSummary.getAverageEnvironmentRating()
        );
        vo.setAverageServiceRating(
                ratingSummary.getAverageServiceRating()
        );
        vo.setRatingCount(ratingSummary.getRatingCount());

        try {
            List<String> environmentTags = objectMapper.readValue(
                    merchant.getEnvironmentTags(),
                    new TypeReference<List<String>>() {
                    }
            );
            vo.setEnvironmentTags(environmentTags);
        } catch (Exception exception) {
            vo.setEnvironmentTags(new ArrayList<>());
        }

        // 获取真实营业时间
        List<MerchantBusinessHours> hoursList = businessHoursMapper.selectByMerchantId(merchantId);
        List<BusinessHourVO> businessHours = new ArrayList<>();
        for (MerchantBusinessHours hour : hoursList) {
            BusinessHourVO hourVO = new BusinessHourVO();
            hourVO.setDayOfWeek(hour.getDayOfWeek());
            hourVO.setOpenTime(hour.getOpenTime());
            hourVO.setCloseTime(hour.getCloseTime());
            hourVO.setIsClosed(hour.getIsClosed());
            hourVO.setCrossesMidnight(hour.getCrossesMidnight());
            businessHours.add(hourVO);
        }
        vo.setBusinessHours(businessHours);

        ReviewSummaryVO summary = new ReviewSummaryVO();
        summary.setReviewCount(
                ratingSummary.getRatingCount() != null
                        ? ratingSummary.getRatingCount().intValue()
                        : 0
        );
        summary.setAdvantages(new ArrayList<>());
        summary.setDisadvantages(new ArrayList<>());
        vo.setReviewSummary(summary);

        return ApiResponse.success(vo);
    }
}