package com.foodadvisor.dto.recommendation;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 单商家在分路语义检索中的匹配结果。
 *
 * 三路检索各自独立计算，最终加权聚合为一个语义分和可信度。
 */
@Data
public class SemanticMatchResult {

    /**
     * 商家编号。
     */
    private Long merchantId;

    // ---- 分路分数 ----

    /**
     * MERCHANT_INTRO 路最高相似度 (0~1)，未命中时为 null。
     */
    private BigDecimal introScore;

    /**
     * MENU 路最高相似度 (0~1)，未命中时为 null。
     */
    private BigDecimal menuScore;

    /**
     * REVIEW 路最高相似度 (0~1)，未命中时为 null。
     */
    private BigDecimal reviewScore;

    // ---- 聚合分数 ----

    /**
     * 三路加权聚合分 (0~1)。
     */
    private BigDecimal weightedScore;

    // ---- 命中统计 ----

    /**
     * 三路合计命中的 chunk 总数。
     */
    private int totalHits;

    /**
     * 覆盖的来源种类数 (1~3)。
     */
    private int sourceDiversity;

    // ---- 可信度 ----

    /**
     * 语义匹配可信度 (0~1)。
     *
     * 综合来源覆盖率、命中数量和加权分数计算。
     * &lt; 0.3 建议标记为低可信度。
     */
    private BigDecimal confidence;

    // ---- 展示依据 ----

    /**
     * 各路上匹配分数最高的 chunk 文本片段，用于前端展示推荐依据。
     */
    private List<SemanticEvidenceItem> evidenceItems = new ArrayList<>();

    // ---- 权重常量 ----

    /** MERCHANT_INTRO 路权重 */
    public static final BigDecimal INTRO_WEIGHT = new BigDecimal("0.50");

    /** MENU 路权重 */
    public static final BigDecimal MENU_WEIGHT = new BigDecimal("0.30");

    /** REVIEW 路权重 */
    public static final BigDecimal REVIEW_WEIGHT = new BigDecimal("0.20");

    // ---- 计算方法 ----

    /**
     * 计算加权聚合分和可信度。
     *
     * 调用时机：三路检索结果都写入后调用一次。
     */
    public void compute() {
        BigDecimal intro = zeroIfNull(introScore);
        BigDecimal menu  = zeroIfNull(menuScore);
        BigDecimal review = zeroIfNull(reviewScore);

        this.weightedScore = INTRO_WEIGHT.multiply(intro)
                .add(MENU_WEIGHT.multiply(menu))
                .add(REVIEW_WEIGHT.multiply(review))
                .setScale(4, RoundingMode.HALF_UP);

        this.totalHits = evidenceItems.size();
        this.sourceDiversity =
                (introScore != null ? 1 : 0)
                + (menuScore  != null ? 1 : 0)
                + (reviewScore != null ? 1 : 0);

        // 可信度 = 0.4×来源覆盖率 + 0.3×命中密度 + 0.3×加权分数
        BigDecimal coverage = new BigDecimal(sourceDiversity)
                .divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP);
        BigDecimal density = new BigDecimal(Math.min(totalHits, 5))
                .divide(new BigDecimal("5"), 4, RoundingMode.HALF_UP);

        this.confidence = new BigDecimal("0.40").multiply(coverage)
                .add(new BigDecimal("0.30").multiply(density))
                .add(new BigDecimal("0.30").multiply(weightedScore))
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 当某路检索失败（如 OpenSearch 不可用）导致该路权重需重新分配时调用。
     *
     * @param effectiveIntroWeight  调整后的 intro 权重
     * @param effectiveMenuWeight   调整后的 menu 权重
     * @param effectiveReviewWeight 调整后的 review 权重
     */
    public void recomputeWithWeights(
            BigDecimal effectiveIntroWeight,
            BigDecimal effectiveMenuWeight,
            BigDecimal effectiveReviewWeight
    ) {
        BigDecimal intro  = zeroIfNull(introScore);
        BigDecimal menu   = zeroIfNull(menuScore);
        BigDecimal review = zeroIfNull(reviewScore);

        this.weightedScore = effectiveIntroWeight.multiply(intro)
                .add(effectiveMenuWeight.multiply(menu))
                .add(effectiveReviewWeight.multiply(review))
                .setScale(4, RoundingMode.HALF_UP);

        // 重新计算可信度时，缺失路的 coverage 贡献减半
        int availableSources =
                (introScore != null ? 1 : 0)
                + (menuScore  != null ? 1 : 0)
                + (reviewScore != null ? 1 : 0);
        this.sourceDiversity = availableSources;
        this.totalHits = evidenceItems.size();

        BigDecimal coverage = new BigDecimal(availableSources)
                .divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("0.67"));  // 有路缺失 → coverage 打折
        BigDecimal density = new BigDecimal(Math.min(totalHits, 5))
                .divide(new BigDecimal("5"), 4, RoundingMode.HALF_UP);

        this.confidence = new BigDecimal("0.40").multiply(coverage)
                .add(new BigDecimal("0.30").multiply(density))
                .add(new BigDecimal("0.30").multiply(weightedScore))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ---- 内部类 ----

    /**
     * 单条语义匹配依据。
     */
    @Data
    public static class SemanticEvidenceItem {
        /** 来源类型 */
        private String sourceType;
        /** 来源 ID */
        private Long sourceId;
        /** 匹配文本片段（截断到 150 字） */
        private String text;
        /** 相似度分数 */
        private BigDecimal score;
    }
}
