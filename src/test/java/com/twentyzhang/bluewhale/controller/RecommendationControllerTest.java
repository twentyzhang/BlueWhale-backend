package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.config.SecurityConfig;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.filter.JwtAuthenticationFilter;
import com.twentyzhang.bluewhale.service.RecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = RecommendationController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}
        )
)
@DisplayName("RecommendationController")
class RecommendationControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private RecommendationService recommendationService;

    private static ProductListItemResponse item(long id) {
        return ProductListItemResponse.builder()
                .id(id).name("商品" + id).price(new BigDecimal("9.90"))
                .stock(10).imageUrl("http://img/" + id).categoryName("数码").build();
    }

    @Test
    @DisplayName("GET /api/products/{id}/recommendations 返回相似商品")
    void related() throws Exception {
        when(recommendationService.getRelated(eq(10L), anyInt()))
                .thenReturn(List.of(item(20), item(30)));

        mvc.perform(get("/api/products/10/recommendations").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(20));
    }

    @Test
    @DisplayName("GET /api/recommendations 返回个性化推荐")
    void personalized() throws Exception {
        when(recommendationService.getPersonalized(anyInt()))
                .thenReturn(List.of(item(20)));

        mvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
