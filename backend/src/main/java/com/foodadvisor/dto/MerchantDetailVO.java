package com.foodadvisor.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class MerchantDetailVO {

    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String category;
    private String cuisine;
    private BigDecimal rating;
    private BigDecimal averagePrice;
    private Long reviewCount;
    private String address;
    private String regionCode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String description;
    private List<String> environmentTags;
    private String platformStatus;
    private String businessStatus;

    /** 营业时间结构化数组 */
    private List<BusinessHourVO> businessHours;

    /** 评价摘要 */
    private ReviewSummaryVO reviewSummary;
}
