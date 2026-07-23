package com.foodadvisor.dto.fraud;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交人工复核请求
 */
@Data
public class ReviewRequest {

    /** 复核结论: CONFIRMED_FRAUD / DISMISSED / NEED_FURTHER_CHECK */
    @NotBlank(message = "复核结论不能为空")
    private String conclusion;

    /** 复核备注 */
    private String remark;
}
