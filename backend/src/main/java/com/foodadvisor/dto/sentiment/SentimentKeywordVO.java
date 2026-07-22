package com.foodadvisor.dto.sentiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 好评关键词 VO — 用于词云/关键词排名展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentKeywordVO {

    /** 关键词文本 */
    private String word;

    /** 出现次数 */
    private Integer count;
}
