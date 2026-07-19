package com.foodadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.MatchedDishVO;
import com.foodadvisor.entity.Dish;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DishMatchingServiceTest {

    private final DishMatchingService service =
            new DishMatchingService(new ObjectMapper());

    @Test
    void nameMatchesTakePriorityAndResultsAreLimitedToThree() {
        ConstraintState constraints = new ConstraintState();
        constraints.setDishKeywords(List.of("水煮鱼"));

        List<MatchedDishVO> matches = service.match(
                constraints,
                Map.of(
                        10L,
                        List.of(
                                dish(1L, "水煮鱼", null, "[]", null),
                                dish(2L, "招牌水煮鱼", null, "[]", new BigDecimal("68")),
                                dish(3L, "鱼片", "水煮鱼风味", "[]", null),
                                dish(4L, "鱼锅", null, "[\"水煮鱼\"]", null)
                        )
                )
        ).get(10L);

        assertEquals(3, matches.size());
        assertEquals("NAME_EXACT", matches.get(0).getMatchType());
        assertEquals(10L, matches.get(0).getMerchantId());
        assertEquals(null, matches.get(0).getDishPrice());
    }

    @Test
    void invalidTagsAndNoRealMatchReturnEmpty() {
        ConstraintState constraints = new ConstraintState();
        constraints.setDishKeywords(List.of("牛肉"));

        assertTrue(service.match(
                constraints,
                Map.of(
                        10L,
                        List.of(dish(
                                1L,
                                "青菜",
                                "时蔬",
                                "{broken",
                                BigDecimal.TEN
                        ))
                )
        ).isEmpty());
    }

    private Dish dish(
            Long id,
            String name,
            String description,
            String tags,
            BigDecimal price
    ) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setMerchantId(10L);
        dish.setName(name);
        dish.setDescription(description);
        dish.setTasteTags(tags);
        dish.setPrice(price);
        return dish;
    }
}
