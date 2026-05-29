package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.ProductDetailResponse;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Result<IPage<ProductListItemResponse>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(
                productService.searchProducts(keyword, categoryId, minPrice, maxPrice, page, size));
    }

    @GetMapping("/{productId}")
    public Result<ProductDetailResponse> getProductById(@PathVariable Long productId) {
        return Result.success(productService.getProductById(productId));
    }
}
