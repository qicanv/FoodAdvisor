package com.foodadvisor.dto.merchant;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateMerchantRequest {

    private String name;
    private String category;
    private String cuisine;
    private BigDecimal averagePrice;
    private String address;
    private String regionCode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String description;
    private String environmentTags;
}
