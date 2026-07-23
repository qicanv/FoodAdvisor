package com.foodadvisor.dto.fraud;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检测扫描响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectResponse {

    /** 本次共创建的案例总数 */
    private int totalCasesCreated;

    /** 各规则的创建明细 */
    private List<RuleDetectDetail> details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleDetectDetail {
        private String ruleType;
        private String ruleTypeText;
        private int casesCreated;
    }
}
