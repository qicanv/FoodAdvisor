package com.foodadvisor.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReviewAnalysisResultVO {

    private Long reviewId;
    private Long merchantId;

    /** V0.3 新增：版本追踪 */
    private Integer reviewVersion;
    private Integer analysisVersion;

    private String sentiment;
    private BigDecimal confidence;
    private Boolean lowConfidence;

    private List<String> keywords;

    /** 方面级情感 */
    private List<AspectVO> aspects;

    /** 标签列表 */
    private List<TagResultVO> tags;

    /** V0.3 新增：差评归因列表 */
    private List<IssueCategoryVO> issueCategories;

    private String negativeReason;
    private String modelName;
    private String modelVersion;

    /** V0.3 新增：AI 追踪ID */
    private String businessTraceId;

    private LocalDateTime analyzedAt;

    @Data
    public static class AspectVO {
        private String category;
        private String sentiment;
        private String text;
    }

    @Data
    public static class TagResultVO {
        private String tagCode;
        private String tagName;
        private String category;
        private String sentiment;
        private BigDecimal confidence;
        private String evidenceText;
    }

    @Data
    public static class IssueCategoryVO {
        private String category;
        private String categoryName;
        private BigDecimal confidence;
        private String evidenceText;
    }
}
