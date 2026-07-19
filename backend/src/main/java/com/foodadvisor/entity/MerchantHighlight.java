package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 商家亮点（EPIC-02 Story 5）
 *
 * 从正面评价中挖掘并缓存商家核心优势，每条亮点
 * 对应一个优势维度（招牌菜 / 环境 / 服务 / 价格 / 品牌特色），
 * 附带提及次数和好评占比，供商家端展示和宣传使用。
 */
@Data
@TableName("merchant_highlights")
public class MerchantHighlight {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属商家ID */
    private Long merchantId;

    /**
     * 亮点类型：
     * SIGNATURE_DISH  — 招牌菜
     * ENVIRONMENT    — 环境特色
     * SERVICE        — 服务特点
     * PRICE          — 价格优势
     * BRAND_FEATURE  — 品牌特色
     */
    private String highlightType;

    /** 亮点标题，如"招牌拿铁广受好评" */
    private String title;

    /** 亮点详细描述，如"超过80%的顾客在评价中提到了招牌拿铁..." */
    private String description;

    /** 提及该亮点的正面评价数量 */
    private Integer mentionCount;

    /** 好评占比（0~1），该亮点关联评价中正面评价的比例 */
    private BigDecimal positiveRatio;

    /** 亮点版本号，同商家递增，每次重新生成时 +1 */
    private Integer version;

    /** ACTIVE / OUTDATED / DISABLED */
    private String status;

    /** 亮点生成时间 */
    private OffsetDateTime generatedAt;
}
