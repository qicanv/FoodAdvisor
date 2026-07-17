package com.foodadvisor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.RecommendationItemVO;
import com.foodadvisor.dto.recommendation.RecommendationScoreItemVO;
import com.foodadvisor.dto.recommendation.RecommendationWeights;
import com.foodadvisor.entity.Merchant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于确定性规则计算商家匹配分。
 *
 * 本类只负责：
 * 1. 硬过滤；
 * 2. 分项评分；
 * 3. 匹配条件与风险说明；
 * 4. 模板化推荐理由。
 *
 * 不调用大模型，也不允许大模型生成或修改分数。
 */
@Component
public class MatchScoreCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal FIVE = new BigDecimal("5");
    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final double EARTH_RADIUS_KM = 6371.0088D;

    private final ObjectMapper objectMapper;

    public MatchScoreCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 对单个商家进行硬过滤和评分。
     *
     * @return 被硬过滤时返回 Optional.empty()；
     *         否则返回完整推荐结果。
     */
    public Optional<RecommendationItemVO> calculate(
            Merchant merchant,
            ConstraintState constraints,
            RecommendationWeights weights,
            BigDecimal userLatitude,
            BigDecimal userLongitude
    ) {
        ConstraintState safeConstraints =
                constraints == null
                        ? new ConstraintState()
                        : constraints;

        if (!passesHardFilters(merchant, safeConstraints)) {
            return Optional.empty();
        }

        RecommendationItemVO result =
                buildBaseResult(merchant);

        List<String> matchedConditions =
                result.getMatchedConditions();

        List<String> riskNotes =
                result.getRiskNotes();

        if (hasValues(safeConstraints.getTasteRestrictions())) {
            riskNotes.add(
                    "口味限制缺少结构化菜品数据，需要进一步向商家确认"
            );
        }

        if (safeConstraints.getBusinessTime() != null
                && !safeConstraints.getBusinessTime().isBlank()) {
            riskNotes.add(
                    "本次仅校验商家经营状态，尚未接入具体营业时段判断"
            );
        }

        BigDecimal finalScore = ZERO;

        FactorResult cuisineResult =
                calculateCuisineFactor(
                        merchant,
                        safeConstraints,
                        matchedConditions,
                        riskNotes
                );

        finalScore = finalScore.add(
                addScoreItem(
                        result.getScoreItems(),
                        "cuisine",
                        weights.getCuisine(),
                        cuisineResult.factor(),
                        cuisineResult.explanation()
                )
        );

        FactorResult ratingResult =
                calculateRatingFactor(
                        merchant,
                        safeConstraints,
                        matchedConditions,
                        riskNotes
                );

        finalScore = finalScore.add(
                addScoreItem(
                        result.getScoreItems(),
                        "rating",
                        weights.getRating(),
                        ratingResult.factor(),
                        ratingResult.explanation()
                )
        );

        FactorResult priceResult =
                calculatePriceFactor(
                        merchant,
                        safeConstraints,
                        matchedConditions,
                        riskNotes
                );

        finalScore = finalScore.add(
                addScoreItem(
                        result.getScoreItems(),
                        "price",
                        weights.getPrice(),
                        priceResult.factor(),
                        priceResult.explanation()
                )
        );

        DistanceResult distanceResult =
                calculateDistanceFactor(
                        merchant,
                        safeConstraints,
                        userLatitude,
                        userLongitude,
                        matchedConditions,
                        riskNotes
                );

        result.setDistanceKm(distanceResult.distanceKm());

        finalScore = finalScore.add(
                addScoreItem(
                        result.getScoreItems(),
                        "distance",
                        weights.getDistance(),
                        distanceResult.factor(),
                        distanceResult.explanation()
                )
        );

        FactorResult environmentResult =
                calculateEnvironmentFactor(
                        merchant,
                        safeConstraints,
                        matchedConditions,
                        riskNotes
                );

        finalScore = finalScore.add(
                addScoreItem(
                        result.getScoreItems(),
                        "environment",
                        weights.getEnvironment(),
                        environmentResult.factor(),
                        environmentResult.explanation()
                )
        );

        FactorResult reputationResult =
                calculateReputationFactor(
                        merchant,
                        matchedConditions,
                        riskNotes
                );

        finalScore = finalScore.add(
                addScoreItem(
                        result.getScoreItems(),
                        "reputation",
                        weights.getReputation(),
                        reputationResult.factor(),
                        reputationResult.explanation()
                )
        );

        /*
         * 优先保留菜系、价格、评分、距离、环境等用户需求匹配项。
         *
         * 当实际匹配项不足两项时，
         * 再使用平台有效和正常经营状态作为补充，
         * 保证每个结果至少展示两项匹配条件。
         */
        if (matchedConditions.size() < 2) {
            matchedConditions.add("商家平台状态有效");
        }

        if (matchedConditions.size() < 2) {
            matchedConditions.add("商家当前处于正常经营状态");
        }

        result.setFinalScore(
                finalScore.setScale(2, RoundingMode.HALF_UP)
        );

        result.setReason(buildReason(result));

        return Optional.of(result);
    }

    /**
     * 硬过滤规则：
     * 1. 已逻辑删除；
     * 2. 平台状态不是 ACTIVE；
     * 3. 经营状态不是 OPERATING；
     * 4. 价格超过预算或价格缺失；
     * 5. 命中明确排除的菜系或商家类型。
     */
    private boolean passesHardFilters(
            Merchant merchant,
            ConstraintState constraints
    ) {
        if (merchant == null) {
            return false;
        }

        if (merchant.getDeletedAt() != null) {
            return false;
        }

        if (!"ACTIVE".equalsIgnoreCase(
                merchant.getPlatformStatus()
        )) {
            return false;
        }

        if (!"OPERATING".equalsIgnoreCase(
                merchant.getOperationStatus()
        )) {
            return false;
        }

        BigDecimal budget =
                resolvePerCapitaBudget(constraints);

        if (budget != null) {
            if (merchant.getAveragePrice() == null) {
                return false;
            }

            if (merchant.getAveragePrice()
                    .compareTo(budget) > 0) {
                return false;
            }
        }

        String merchantText =
                buildMerchantSearchText(merchant);

        if (containsAny(
                merchantText,
                constraints.getExcludedCuisines()
        )) {
            return false;
        }

        return !containsAny(
                merchantText,
                constraints.getExcludedMerchantTypes()
        );
    }

    private RecommendationItemVO buildBaseResult(
            Merchant merchant
    ) {
        RecommendationItemVO result =
                new RecommendationItemVO();

        result.setMerchantId(merchant.getId());
        result.setMerchantName(merchant.getName());
        result.setCategory(merchant.getCategory());
        result.setCuisine(merchant.getCuisine());
        result.setMerchantRating(merchant.getRating());
        result.setAveragePrice(merchant.getAveragePrice());
        result.setReviewCount(merchant.getReviewCount());

        return result;
    }

    private FactorResult calculateCuisineFactor(
            Merchant merchant,
            ConstraintState constraints,
            List<String> matchedConditions,
            List<String> riskNotes
    ) {
        Set<String> preferences = new LinkedHashSet<>();

        addNonBlankValues(
                preferences,
                constraints.getCuisines()
        );

        addNonBlankValues(
                preferences,
                constraints.getMerchantTypes()
        );

        if (preferences.isEmpty()) {
            return new FactorResult(
                    HALF,
                    "用户未指定菜系或商家类型，使用中性系数0.5"
            );
        }

        String merchantText =
                buildMerchantSearchText(merchant);

        List<String> matchedValues =
                preferences.stream()
                        .filter(value ->
                                containsText(
                                        merchantText,
                                        value
                                )
                        )
                        .toList();

        if (!matchedValues.isEmpty()) {
            String joined =
                    String.join("、", matchedValues);

            matchedConditions.add(
                    "菜系或商家类型匹配：" + joined
            );

            return new FactorResult(
                    ONE,
                    "命中用户偏好的菜系或商家类型：" + joined
            );
        }

        riskNotes.add("未命中用户偏好的菜系或商家类型");

        return new FactorResult(
                ZERO,
                "商家菜系和类型均未命中用户偏好"
        );
    }

    private FactorResult calculateRatingFactor(
            Merchant merchant,
            ConstraintState constraints,
            List<String> matchedConditions,
            List<String> riskNotes
    ) {
        BigDecimal rating = merchant.getRating();

        if (rating == null) {
            riskNotes.add("商家暂无综合评分");

            return new FactorResult(
                    ZERO,
                    "商家评分数据缺失"
            );
        }

        BigDecimal factor = rating
                .divide(FIVE, 6, RoundingMode.HALF_UP);

        factor = clampFactor(factor);

        BigDecimal minRating =
                constraints.getMinRating();

        if (minRating != null) {
            if (rating.compareTo(minRating) >= 0) {
                matchedConditions.add(
                        "评分达到要求（"
                                + formatNumber(rating)
                                + "分）"
                );
            } else {
                riskNotes.add(
                        "评分低于用户期望（"
                                + formatNumber(rating)
                                + " < "
                                + formatNumber(minRating)
                                + "）"
                );
            }
        } else if (rating.compareTo(
                new BigDecimal("4.5")
        ) >= 0) {
            matchedConditions.add(
                    "商家评分较高（"
                            + formatNumber(rating)
                            + "分）"
            );
        }

        return new FactorResult(
                factor,
                "商家评分"
                        + formatNumber(rating)
                        + "分，按5分制折算"
        );
    }

    private FactorResult calculatePriceFactor(
            Merchant merchant,
            ConstraintState constraints,
            List<String> matchedConditions,
            List<String> riskNotes
    ) {
        BigDecimal budget =
                resolvePerCapitaBudget(constraints);

        BigDecimal averagePrice =
                merchant.getAveragePrice();

        if (budget == null) {
            if (averagePrice == null) {
                riskNotes.add("商家人均价格信息缺失");

                return new FactorResult(
                        HALF,
                        "用户未指定预算且商家价格缺失，使用中性系数0.5"
                );
            }

            return new FactorResult(
                    HALF,
                    "用户未指定人均预算，价格项使用中性系数0.5"
            );
        }

        /*
         * 有预算时，价格缺失和超预算商家已经被硬过滤。
         */
        BigDecimal ratio = averagePrice.divide(
                budget,
                6,
                RoundingMode.HALF_UP
        );

        BigDecimal factor;

        if (ratio.compareTo(
                new BigDecimal("0.8")
        ) <= 0) {
            factor = ONE;
        } else {
            /*
             * 预算80%以内为满分。
             * 80%～100%之间线性扣除最多30%。
             */
            BigDecimal excessRatio = ratio
                    .subtract(new BigDecimal("0.8"))
                    .divide(
                            new BigDecimal("0.2"),
                            6,
                            RoundingMode.HALF_UP
                    );

            BigDecimal deduction = excessRatio
                    .multiply(new BigDecimal("0.3"));

            factor = ONE.subtract(deduction);
        }

        factor = clampFactor(factor);

        matchedConditions.add(
                "人均价格"
                        + formatNumber(averagePrice)
                        + "元，未超过预算"
                        + formatNumber(budget)
                        + "元"
        );

        if (ratio.compareTo(
                new BigDecimal("0.9")
        ) >= 0) {
            riskNotes.add("人均价格接近预算上限");
        }

        return new FactorResult(
                factor,
                "商家人均价格"
                        + formatNumber(averagePrice)
                        + "元，预算"
                        + formatNumber(budget)
                        + "元"
        );
    }

    private DistanceResult calculateDistanceFactor(
            Merchant merchant,
            ConstraintState constraints,
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            List<String> matchedConditions,
            List<String> riskNotes
    ) {
        if (userLatitude == null
                || userLongitude == null) {
            riskNotes.add("未提供用户位置，本次不计距离得分");

            return new DistanceResult(
                    ZERO,
                    null,
                    "用户位置缺失，距离得分为0"
            );
        }

        if (merchant.getLatitude() == null
                || merchant.getLongitude() == null) {
            riskNotes.add("商家坐标缺失，本次不计距离得分");

            return new DistanceResult(
                    ZERO,
                    null,
                    "商家坐标缺失，距离得分为0"
            );
        }

        BigDecimal actualDistance =
                calculateDistanceKm(
                        userLatitude,
                        userLongitude,
                        merchant.getLatitude(),
                        merchant.getLongitude()
                );

        BigDecimal maximumDistance =
                constraints.getDistanceKm();

        BigDecimal factor;

        if (maximumDistance != null
                && maximumDistance.compareTo(ZERO) > 0) {
            factor = ONE.subtract(
                    actualDistance.divide(
                            maximumDistance,
                            6,
                            RoundingMode.HALF_UP
                    )
            );

            factor = clampFactor(factor);

            if (actualDistance.compareTo(
                    maximumDistance
            ) <= 0) {
                matchedConditions.add(
                        "距离约"
                                + formatNumber(actualDistance)
                                + "公里，位于期望范围内"
                );
            } else {
                riskNotes.add(
                        "距离约"
                                + formatNumber(actualDistance)
                                + "公里，超过期望的"
                                + formatNumber(maximumDistance)
                                + "公里"
                );
            }
        } else {
            /*
             * 用户未指定最大距离时，仍可按实际距离排序。
             * 每增加5公里，匹配系数逐步下降。
             */
            factor = ONE.divide(
                    ONE.add(
                            actualDistance.divide(
                                    new BigDecimal("5"),
                                    6,
                                    RoundingMode.HALF_UP
                            )
                    ),
                    6,
                    RoundingMode.HALF_UP
            );

            matchedConditions.add(
                    "已计算实际距离约"
                            + formatNumber(actualDistance)
                            + "公里"
            );
        }

        return new DistanceResult(
                clampFactor(factor),
                actualDistance,
                "系统根据用户坐标和商家坐标计算距离为"
                        + formatNumber(actualDistance)
                        + "公里"
        );
    }

    private FactorResult calculateEnvironmentFactor(
            Merchant merchant,
            ConstraintState constraints,
            List<String> matchedConditions,
            List<String> riskNotes
    ) {
        Set<String> requirements =
                new LinkedHashSet<>();

        addNonBlankValues(
                requirements,
                constraints.getEnvironmentRequirements()
        );

        addNonBlankValues(
                requirements,
                constraints.getScenes()
        );

        if (requirements.isEmpty()) {
            return new FactorResult(
                    HALF,
                    "用户未指定环境或消费场景，使用中性系数0.5"
            );
        }

        List<String> merchantTags =
                parseEnvironmentTags(
                        merchant.getEnvironmentTags()
                );

        List<String> matchedTags =
                requirements.stream()
                        .filter(requirement ->
                                merchantTags.stream()
                                        .anyMatch(tag ->
                                                containsText(
                                                        tag,
                                                        requirement
                                                )
                                                        || containsText(
                                                        requirement,
                                                        tag
                                                )
                                        )
                        )
                        .toList();

        BigDecimal factor = new BigDecimal(
                matchedTags.size()
        ).divide(
                new BigDecimal(requirements.size()),
                6,
                RoundingMode.HALF_UP
        );

        if (!matchedTags.isEmpty()) {
            matchedConditions.add(
                    "环境或场景匹配："
                            + String.join("、", matchedTags)
            );
        }

        if (matchedTags.size() < requirements.size()) {
            Set<String> unmatched =
                    new LinkedHashSet<>(requirements);

            unmatched.removeAll(matchedTags);

            riskNotes.add(
                    "环境或场景要求未完全匹配："
                            + String.join("、", unmatched)
            );
        }

        return new FactorResult(
                clampFactor(factor),
                "环境标签命中"
                        + matchedTags.size()
                        + "/"
                        + requirements.size()
                        + "项"
        );
    }

    private FactorResult calculateReputationFactor(
            Merchant merchant,
            List<String> matchedConditions,
            List<String> riskNotes
    ) {
        BigDecimal ratingFactor =
                merchant.getRating() == null
                        ? ZERO
                        : clampFactor(
                                merchant.getRating().divide(
                                        FIVE,
                                        6,
                                        RoundingMode.HALF_UP
                                )
                        );

        int reviewCount =
                merchant.getReviewCount() == null
                        ? 0
                        : Math.max(
                                merchant.getReviewCount(),
                                0
                        );

        BigDecimal reviewFactor =
                new BigDecimal(reviewCount)
                        .divide(
                                ONE_HUNDRED,
                                6,
                                RoundingMode.HALF_UP
                        );

        reviewFactor = clampFactor(reviewFactor);

        BigDecimal factor = ratingFactor
                .multiply(new BigDecimal("0.6"))
                .add(
                        reviewFactor.multiply(
                                new BigDecimal("0.4")
                        )
                );

        if (reviewCount >= 50) {
            matchedConditions.add(
                    "评论数量较多（"
                            + reviewCount
                            + "条）"
            );
        } else if (reviewCount < 10) {
            riskNotes.add(
                    "评论数量较少（"
                            + reviewCount
                            + "条）"
            );
        }

        return new FactorResult(
                clampFactor(factor),
                "口碑系数由60%评分因子和40%评论数量因子计算"
        );
    }

    private BigDecimal addScoreItem(
            Map<String, RecommendationScoreItemVO> scoreItems,
            String itemName,
            BigDecimal weight,
            BigDecimal factor,
            String explanation
    ) {
        BigDecimal safeWeight =
                weight == null ? ZERO : weight;

        BigDecimal safeFactor =
                clampFactor(factor);

        BigDecimal score = safeWeight
                .multiply(safeFactor)
                .setScale(2, RoundingMode.HALF_UP);

        RecommendationScoreItemVO item =
                new RecommendationScoreItemVO(
                        safeWeight.setScale(
                                2,
                                RoundingMode.HALF_UP
                        ),
                        safeFactor.setScale(
                                4,
                                RoundingMode.HALF_UP
                        ),
                        score,
                        explanation
                );

        scoreItems.put(itemName, item);

        return score;
    }

    private BigDecimal resolvePerCapitaBudget(
            ConstraintState constraints
    ) {
        if (constraints.getPerCapitaBudget() != null
                && constraints.getPerCapitaBudget()
                .compareTo(ZERO) > 0) {
            return constraints.getPerCapitaBudget();
        }

        if (constraints.getTotalBudget() != null
                && constraints.getTotalBudget()
                .compareTo(ZERO) > 0
                && constraints.getPartySize() != null
                && constraints.getPartySize() > 0) {
            return constraints.getTotalBudget().divide(
                    new BigDecimal(
                            constraints.getPartySize()
                    ),
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return null;
    }

    private BigDecimal calculateDistanceKm(
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            BigDecimal merchantLatitude,
            BigDecimal merchantLongitude
    ) {
        double lat1 =
                Math.toRadians(userLatitude.doubleValue());

        double lon1 =
                Math.toRadians(userLongitude.doubleValue());

        double lat2 =
                Math.toRadians(merchantLatitude.doubleValue());

        double lon2 =
                Math.toRadians(merchantLongitude.doubleValue());

        double latitudeDifference = lat2 - lat1;
        double longitudeDifference = lon2 - lon1;

        double haversine =
                Math.pow(
                        Math.sin(latitudeDifference / 2D),
                        2D
                )
                        + Math.cos(lat1)
                        * Math.cos(lat2)
                        * Math.pow(
                        Math.sin(
                                longitudeDifference / 2D
                        ),
                        2D
                );

        double centralAngle =
                2D * Math.atan2(
                        Math.sqrt(haversine),
                        Math.sqrt(1D - haversine)
                );

        return BigDecimal.valueOf(
                EARTH_RADIUS_KM * centralAngle
        ).setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> parseEnvironmentTags(
            String environmentTagsJson
    ) {
        if (environmentTagsJson == null
                || environmentTagsJson.isBlank()) {
            return List.of();
        }

        try {
            List<String> tags = objectMapper.readValue(
                    environmentTagsJson,
                    new TypeReference<List<String>>() {
                    }
            );

            return tags == null
                    ? List.of()
                    : tags.stream()
                    .filter(value ->
                            value != null
                                    && !value.isBlank()
                    )
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String buildMerchantSearchText(
            Merchant merchant
    ) {
        return String.join(
                " ",
                safeText(merchant.getName()),
                safeText(merchant.getCategory()),
                safeText(merchant.getCuisine()),
                safeText(merchant.getDescription()),
                safeText(merchant.getEnvironmentTags())
        ).toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(
            String merchantText,
            List<String> values
    ) {
        if (!hasValues(values)) {
            return false;
        }

        return values.stream()
                .filter(value ->
                        value != null
                                && !value.isBlank()
                )
                .anyMatch(value ->
                        containsText(
                                merchantText,
                                value
                        )
                );
    }

    private boolean containsText(
            String source,
            String target
    ) {
        if (source == null
                || target == null
                || target.isBlank()) {
            return false;
        }

        return source.toLowerCase(Locale.ROOT)
                .contains(
                        target.trim()
                                .toLowerCase(Locale.ROOT)
                );
    }

    private void addNonBlankValues(
            Set<String> target,
            List<String> source
    ) {
        if (source == null) {
            return;
        }

        source.stream()
                .filter(value ->
                        value != null
                                && !value.isBlank()
                )
                .map(String::trim)
                .forEach(target::add);
    }

    private boolean hasValues(List<String> values) {
        return values != null
                && values.stream().anyMatch(value ->
                value != null && !value.isBlank()
        );
    }

    private BigDecimal clampFactor(BigDecimal factor) {
        if (factor == null
                || factor.compareTo(ZERO) < 0) {
            return ZERO;
        }

        if (factor.compareTo(ONE) > 0) {
            return ONE;
        }

        return factor;
    }

    private String buildReason(
            RecommendationItemVO result
    ) {
        String mainMatches =
                result.getMatchedConditions()
                        .stream()
                        .limit(2)
                        .collect(
                                Collectors.joining("、")
                        );

        StringBuilder reason =
                new StringBuilder();

        reason.append(result.getMerchantName())
                .append("满足")
                .append(mainMatches)
                .append("，综合匹配分为")
                .append(formatNumber(
                        result.getFinalScore()
                ))
                .append("分。");

        if (!result.getRiskNotes().isEmpty()) {
            reason.append("需要注意：")
                    .append(
                            result.getRiskNotes().get(0)
                    )
                    .append("。");
        }

        return reason.toString();
    }

    private String formatNumber(BigDecimal value) {
        if (value == null) {
            return "未知";
        }

        return value.stripTrailingZeros()
                .toPlainString();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private record FactorResult(
            BigDecimal factor,
            String explanation
    ) {
    }

    private record DistanceResult(
            BigDecimal factor,
            BigDecimal distanceKm,
            String explanation
    ) {
    }
}