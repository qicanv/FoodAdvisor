package com.foodadvisor.dto.sentiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 差评问题归类 VO — 用于差评问题的排名展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentIssueVO {

    /** 问题类别编码，如 SERVING_SPEED */
    private String category;

    /** 问题类别中文名，如"上菜速度" */
    private String categoryName;

    /** 提及次数 */
    private Integer count;

    /** 占比 (0-100) */
    private Double percentage;
}
