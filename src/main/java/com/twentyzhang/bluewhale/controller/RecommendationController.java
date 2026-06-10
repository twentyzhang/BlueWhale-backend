package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RecommendationController {

    private static final int MAX_LIMIT = 50;

    private final RecommendationService recommendationService;

    /** 接口 A：商品相关推荐（开放，游客可访问）。 */
    @GetMapping("/api/products/{productId}/recommendations")
    public Result<List<ProductListItemResponse>> related(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(recommendationService.getRelated(productId, clamp(limit)));
    }

    /** 接口 B：个性化猜你喜欢（需登录，userId 从 JWT 取）。 */
    @GetMapping("/api/recommendations")
    public Result<List<ProductListItemResponse>> personalized(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(recommendationService.getPersonalized(clamp(limit)));
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
