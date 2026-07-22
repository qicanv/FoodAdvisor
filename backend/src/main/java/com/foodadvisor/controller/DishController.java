package com.foodadvisor.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.entity.Dish;
import com.foodadvisor.mapper.DishMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/dishes")
public class DishController {

    private final DishMapper dishMapper;
    private final ObjectMapper objectMapper;

    public DishController(DishMapper dishMapper, ObjectMapper objectMapper) {
        this.dishMapper = dishMapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<Dish>> listByMerchant(@RequestParam Long merchantId) {
        List<Dish> dishes = dishMapper.selectByMerchantId(merchantId);

        // 公开接口只返回上架中的菜品
        List<Dish> activeDishes = new ArrayList<>();
        for (Dish dish : dishes) {
            if ("ACTIVE".equals(dish.getStatus())) {
                if (dish.getTasteTags() != null) {
                    try {
                        objectMapper.readValue(
                                dish.getTasteTags(),
                                new TypeReference<List<String>>() {}
                        );
                    } catch (Exception ignored) {
                    }
                }
                activeDishes.add(dish);
            }
        }

        return ApiResponse.success(activeDishes);
    }
}