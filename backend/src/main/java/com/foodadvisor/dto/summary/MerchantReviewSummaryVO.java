package com.foodadvisor.dto.summary;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

/** 商家评价摘要展示 VO */
@Data
public class MerchantReviewSummaryVO {

    private Long summaryId;
    private Long merchantId;
    private Integer version;

    /** SUCCESS / INSUFFICIENT_DATA / NONE（从未生成过） */
    private String status;

    private String summaryText;
    private List<SummaryPointVO> advantages;
    private List<SummaryPointVO> disadvantages;
    private List<SummaryPointVO> recommendedDishes;

    /** {text, reviewIds}，为空对象时前端显示"暂无足够信息" */
    private JsonNode environmentSummary;
    private JsonNode serviceSummary;

    /** [{text, direction, reviewIds}] */
    private JsonNode recentChanges;

    private Integer reviewCount;
    /** 评论不足时告知前端门槛值 */
    private Integer minimumReviewCount;

    private OffsetDateTime generatedAt;
}