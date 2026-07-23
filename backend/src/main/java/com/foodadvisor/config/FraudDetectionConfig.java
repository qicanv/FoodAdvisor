package com.foodadvisor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 刷评检测规则配置，从 application.yml 读取
 */
@Data
@Component
@ConfigurationProperties(prefix = "fraud-detection")
public class FraudDetectionConfig {

    /** 规则配置 */
    private Rules rules = new Rules();

    @Data
    public static class Rules {
        private ConcentrationRule concentration = new ConcentrationRule();
        private SimilarityRule similarity = new SimilarityRule();
        private FrequencyRule frequency = new FrequencyRule();
        private RatingAnomalyRule ratingAnomaly = new RatingAnomalyRule();
    }

    @Data
    public static class ConcentrationRule {
        private boolean enabled = true;
        /** 时间窗口（分钟） */
        private int windowMinutes = 60;
        /** 阈值数量 */
        private int thresholdCount = 10;
        /** 风险等级 */
        private String riskLevel = "HIGH";
    }

    @Data
    public static class SimilarityRule {
        private boolean enabled = true;
        /** 相似度阈值 0~1 */
        private double threshold = 0.8;
        /** 最少相似评价数量 */
        private int minGroupSize = 10;
        private String riskLevel = "MEDIUM";
    }

    @Data
    public static class FrequencyRule {
        private boolean enabled = true;
        /** 时间窗口（小时） */
        private int windowHours = 24;
        /** 阈值数量 */
        private int thresholdCount = 100;
        private String riskLevel = "MEDIUM";
    }

    @Data
    public static class RatingAnomalyRule {
        private boolean enabled = true;
        /** 时间窗口（分钟） */
        private int windowMinutes = 60;
        /** 最少评价数量 */
        private int minCount = 100;
        /** 同一评分占比阈值 */
        private double sameRatingRatio = 0.9;
        private String riskLevel = "LOW";
    }
}
