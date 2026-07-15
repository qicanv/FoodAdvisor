package com.foodadvisor.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class ReviewSummaryVO {

    private Long summaryId;
    private Integer version;
    private String status;
    private String summaryText;

    /** 优点列表，每项含 name + mentionCount */
    private List<SummaryItemVO> advantages;

    /** 缺点列表 */
    private List<SummaryItemVO> disadvantages;

    /** 推荐菜品 */
    private List<String> recommendedDishes;

    private Integer reviewCount;
    private OffsetDateTime generatedAt;

    @Data
    public static class SummaryItemVO {
        private String name;
        private Integer mentionCount;
    }
}
