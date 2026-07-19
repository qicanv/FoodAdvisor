package com.foodadvisor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.MatchedDishVO;
import com.foodadvisor.entity.Dish;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class DishMatchingService {

    private static final int MAX_MATCHES_PER_MERCHANT = 3;
    private static final int MIN_REVERSE_CONTAINS_LENGTH = 2;

    private final ObjectMapper objectMapper;

    public DishMatchingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<Long, List<MatchedDishVO>> match(
            ConstraintState constraints,
            Map<Long, List<Dish>> dishesByMerchant
    ) {
        List<String> keywords = new ArrayList<>(
                sanitize(
                        constraints == null
                                ? null
                                : constraints.getDishKeywords()
                )
        );
        if (constraints != null) {
            for (String preference :
                    sanitize(constraints.getTastePreferences())) {
                if (!keywords.contains(preference)) {
                    keywords.add(preference);
                }
            }
        }
        if (keywords.isEmpty() || dishesByMerchant == null) {
            return Map.of();
        }

        Map<Long, List<MatchedDishVO>> result = new LinkedHashMap<>();
        dishesByMerchant.forEach((merchantId, dishes) -> {
            List<MatchedDishVO> matches = new ArrayList<>();
            for (Dish dish : dishes == null ? List.<Dish>of() : dishes) {
                if (dish == null || !merchantId.equals(dish.getMerchantId())) {
                    continue;
                }
                MatchedDishVO best = bestMatch(dish, keywords);
                if (best != null) {
                    matches.add(best);
                }
            }
            matches.sort(
                    Comparator.comparing(
                                    MatchedDishVO::getMatchScore,
                                    Comparator.reverseOrder()
                            )
                            .thenComparing(MatchedDishVO::getDishId)
            );
            if (!matches.isEmpty()) {
                result.put(
                        merchantId,
                        new ArrayList<>(
                                matches.subList(
                                        0,
                                        Math.min(
                                                MAX_MATCHES_PER_MERCHANT,
                                                matches.size()
                                        )
                                )
                        )
                );
            }
        });
        return result;
    }

    private MatchedDishVO bestMatch(Dish dish, List<String> keywords) {
        String name = normalize(dish.getName());
        String description = normalize(dish.getDescription());
        String category = normalize(dish.getCategory());
        Set<String> tags = parseTags(dish);
        MatchedDishVO best = null;

        for (String rawKeyword : keywords) {
            String keyword = normalize(rawKeyword);
            Match match = null;
            if (name.equals(keyword)) {
                match = new Match("NAME_EXACT", "菜名精确匹配", new BigDecimal("1.00"));
            } else if (name.contains(keyword)
                    || (name.length() >= MIN_REVERSE_CONTAINS_LENGTH
                    && keyword.contains(name))) {
                match = new Match("NAME", "菜名包含关键词", new BigDecimal("0.95"));
            } else if (!description.isEmpty() && description.contains(keyword)) {
                match = new Match("DESCRIPTION", "菜单描述包含关键词", new BigDecimal("0.75"));
            } else if (tags.stream().anyMatch(tag ->
                    tag.equals(keyword) || tag.contains(keyword)
                            || keyword.contains(tag))) {
                match = new Match("TAG", "菜品标签包含关键词", new BigDecimal("0.65"));
            } else if (!category.isEmpty() && category.contains(keyword)) {
                match = new Match("CATEGORY", "菜品分类包含关键词", new BigDecimal("0.55"));
            }
            if (match != null
                    && (best == null
                    || match.score().compareTo(best.getMatchScore()) > 0)) {
                best = toVo(dish, rawKeyword, match);
            }
        }
        return best;
    }

    private MatchedDishVO toVo(
            Dish dish,
            String keyword,
            Match match
    ) {
        MatchedDishVO vo = new MatchedDishVO();
        vo.setDishId(dish.getId());
        vo.setMerchantId(dish.getMerchantId());
        vo.setDishName(dish.getName());
        vo.setDishPrice(dish.getPrice());
        vo.setCategory(dish.getCategory());
        vo.setMatchType(match.type());
        vo.setMatchedKeyword(keyword);
        vo.setMatchReason(match.reason());
        vo.setMatchScore(match.score());
        return vo;
    }

    private Set<String> parseTags(Dish dish) {
        if (dish.getTasteTags() == null || dish.getTasteTags().isBlank()) {
            return Set.of();
        }
        try {
            List<String> values = objectMapper.readValue(
                    dish.getTasteTags(),
                    new TypeReference<List<String>>() {
                    }
            );
            return new LinkedHashSet<>(sanitize(values));
        } catch (Exception exception) {
            log.warn(
                    "Ignoring invalid taste_tags JSON for dishId={}",
                    dish.getId()
            );
            return Set.of();
        }
    }

    private List<String> sanitize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !normalize(value).isEmpty()) {
                result.add(value.trim());
            }
        }
        return new ArrayList<>(result);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，。！？、；：,.!?;:]+", "")
                .replaceFirst("^(招牌|特色)", "")
                .trim();
    }

    private record Match(
            String type,
            String reason,
            BigDecimal score
    ) {
    }
}
