package com.foodadvisor.backend.controller;

import com.foodadvisor.backend.entity.Restaurant;
import com.foodadvisor.backend.service.RestaurantService;
import com.foodadvisor.service.AuditLogService;
import com.foodadvisor.util.SensitiveLogSanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantController.class)
class RestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantService restaurantService;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private SensitiveLogSanitizer sensitiveLogSanitizer;

    @Test
    void shouldReturnRestaurantList() throws Exception {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("FoodAdvisor Test Restaurant");
        restaurant.setCategory("Chinese");
        restaurant.setAddress("Test Address");
        restaurant.setAveragePrice(new BigDecimal("68.00"));
        restaurant.setRating(new BigDecimal("4.50"));
        restaurant.setCreatedAt(OffsetDateTime.parse("2026-07-14T14:31:49Z"));
        restaurant.setUpdatedAt(OffsetDateTime.parse("2026-07-14T14:31:49Z"));

        when(restaurantService.listRestaurants())
                .thenReturn(List.of(restaurant));

        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Request succeeded"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name")
                        .value("FoodAdvisor Test Restaurant"))
                .andExpect(jsonPath("$.data[0].category").value("Chinese"))
                .andExpect(jsonPath("$.data[0].averagePrice").value(68.00))
                .andExpect(jsonPath("$.data[0].rating").value(4.50));
    }
}
