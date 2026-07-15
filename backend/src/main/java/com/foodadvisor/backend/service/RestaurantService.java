package com.foodadvisor.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.backend.entity.Restaurant;
import com.foodadvisor.backend.mapper.RestaurantMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RestaurantService {

    private final RestaurantMapper restaurantMapper;

    public RestaurantService(RestaurantMapper restaurantMapper) {
        this.restaurantMapper = restaurantMapper;
    }

    public List<Restaurant> listRestaurants() {
        return restaurantMapper.selectList(
                new LambdaQueryWrapper<Restaurant>()
                        .orderByAsc(Restaurant::getId)
        );
    }
}