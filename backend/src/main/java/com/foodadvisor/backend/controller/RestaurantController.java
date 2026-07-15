package com.foodadvisor.backend.controller;

import com.foodadvisor.backend.common.ApiResponse;
import com.foodadvisor.backend.entity.Restaurant;
import com.foodadvisor.backend.service.RestaurantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping
    public ApiResponse<List<Restaurant>> listRestaurants() {
        return ApiResponse.success(
                restaurantService.listRestaurants()
        );
    }
}