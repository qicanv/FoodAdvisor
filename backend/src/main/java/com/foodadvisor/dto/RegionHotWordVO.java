package com.foodadvisor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 区域热词响应 VO
 *
 * 前端通过此对象渲染区域热词榜单页面。
 * 每个热词包含热度分数、提及次数、关联商家数等指标，
 * 以及可选的关联商家列表（用于"点热词看相关商家"交互）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionHotWordVO {

    /** 热词 ID */
    private Long id;

    /** 区域编码 */
    private String regionCode;

    /** 热词文本 */
    private String word;

    /** 分类：TASTE / SERVICE / ENVIRONMENT / PRICE / SPEED / GENERAL */
    private String category;

    /** 情感倾向：POSITIVE / NEUTRAL / NEGATIVE */
    private String sentiment;

    /** 热度分数 0.00 ~ 100.00 */
    private BigDecimal heatScore;

    /** 提及次数 */
    private Integer mentionCount;

    /** 关联评价数 */
    private Integer reviewCount;

    /** 关联商家数 */
    private Integer merchantCount;

    /** 正面评价占比 */
    private BigDecimal positiveRatio;

    /** 统计周期类型 */
    private String periodType;

    /** 统计起始日期 */
    private LocalDate periodStart;

    /** 统计截止日期 */
    private LocalDate periodEnd;

    /**
     * 关联商家简要信息列表（可选，点击热词时展示）。
     * 仅在请求指定 includeMerchants=true 时填充。
     */
    private List<HotWordMerchantBrief> associatedMerchants;

    /**
     * 热词关联商家简要信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotWordMerchantBrief {

        /** 商家 ID */
        private Long merchantId;

        /** 商家名称 */
        private String merchantName;

        /** 商家分类 */
        private String category;

        /** 该热词在此商家中被提及的次数 */
        private Integer mentionCount;
    }

    /**
     * 热词关联的评价简要信息（点击热词查看原始评价）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotWordReviewBrief {

        /** 评价 ID */
        private Long reviewId;

        /** 评价内容摘要（截取前 200 字） */
        private String content;

        /** 评价评分 1-5 */
        private Integer rating;

        /** 评价时间 */
        private String reviewTime;

        /** 商家 ID */
        private Long merchantId;

        /** 商家名称 */
        private String merchantName;

        /** 数据来源：AI_TAG / KEYWORD_EXTRACT */
        private String sourceType;
    }

    /**
     * 区域简要信息（用于区域列表接口）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionBriefVO {

        /** 区域编码 */
        private String regionCode;

        /** 该区域热词总数 */
        private Integer hotWordCount;

        /** 该区域最热的词（用于预览） */
        private String topWord;
    }
}
