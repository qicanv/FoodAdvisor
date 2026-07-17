package com.foodadvisor.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 差评归因统计 VO — 每个问题类别的聚合数据
 */
@Data
public class IssueStatVO {

    /** 类别编码，如 HYGIENE */
    private String categoryCode;

    /** 类别名称，如 卫生问题 */
    private String categoryName;

    /** 该类别问题数量 */
    private Long count;

    /** 该类别在所有差评问题中的百分比 */
    private BigDecimal percentage;
}
