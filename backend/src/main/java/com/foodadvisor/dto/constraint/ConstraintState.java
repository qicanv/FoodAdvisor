package com.foodadvisor.dto.constraint;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 当前会话中的结构化消费需求。
 *
 * 该对象既用于保存本轮提取结果，
 * 也用于表示多轮对话合并后的完整条件。
 */
@Data
public class ConstraintState {

    /**
     * 用餐人数。
     * 例如：“四个人” -> 4
     */
    private Integer partySize;

    /**
     * 总预算。
     * 例如：“总共两百元” -> 200
     */
    private BigDecimal totalBudget;

    /**
     * 人均预算。
     * 例如：“人均八十” -> 80
     */
    private BigDecimal perCapitaBudget;

    /**
     * 商家类型。
     * 例如：火锅、烧烤、自助餐。
     */
    private List<String> merchantTypes = new ArrayList<>();

    /**
     * 菜系。
     * 例如：川菜、粤菜、湘菜。
     */
    private List<String> cuisines = new ArrayList<>();

    /**
     * 正向口味偏好。
     * 例如：少辣、清淡、酸甜。
     */
    private List<String> tastePreferences = new ArrayList<>();

    /**
     * 必须遵守的口味限制。
     * 例如：不吃辣、不能吃花生。
     */
    private List<String> tasteRestrictions = new ArrayList<>();

    /**
     * 明确排除的菜系。
     * 例如：不要川菜。
     */
    private List<String> excludedCuisines = new ArrayList<>();

    /**
     * 明确排除的商家类型。
     * 例如：不要火锅、不要烧烤。
     */
    private List<String> excludedMerchantTypes = new ArrayList<>();

    /**
     * 最大距离，单位为公里。
     * 例如：“三公里内” -> 3
     */
    private BigDecimal distanceKm;

    /**
     * 最低评分。
     * 例如：“至少4.5分” -> 4.5
     */
    private BigDecimal minRating;

    /**
     * 用餐场景。
     * 例如：朋友聚会、约会、家庭聚餐。
     */
    private List<String> scenes = new ArrayList<>();

    /**
     * 环境要求。
     * 例如：安静、适合拍照。
     */
    private List<String> environmentRequirements =
            new ArrayList<>();

    /**
     * 营业时间要求。
     * 例如：NOW_OPEN、TONIGHT、LATE_NIGHT。
     */
    private String businessTime;
}