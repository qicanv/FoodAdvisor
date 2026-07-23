package com.foodadvisor.dto.fraud;

import lombok.Data;
import java.util.List;

/**
 * 手动触发检测扫描请求
 */
@Data
public class DetectRequest {

    /** 指定要执行的规则类型，不传则执行全部启用的规则 */
    private List<String> ruleTypes;

    /** 指定商家ID，不传则扫描全部商家 */
    private Long merchantId;
}
