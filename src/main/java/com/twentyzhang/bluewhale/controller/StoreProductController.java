package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.CreateProductRequest;
import com.twentyzhang.bluewhale.dto.IdResponse;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.dto.UpdateProductRequest;
import com.twentyzhang.bluewhale.dto.UpdateStockRequest;
import com.twentyzhang.bluewhale.dto.UpdateStockResponse;
import com.twentyzhang.bluewhale.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/stores/{storeId}/products")
@RequiredArgsConstructor
public class StoreProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<IPage<ProductListItemResponse>> getProductsByStore(
            @PathVariable Long storeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(
                productService.getProductsByStore(storeId, keyword, categoryId, minPrice, maxPrice, page, size));
    }

    // 需要鉴权 仅 Staff（本店）
    @PostMapping
    public ApiResponse<IdResponse> createProduct(@PathVariable Long storeId,
                                                  @RequestBody @Valid CreateProductRequest request) {
        return ApiResponse.success(IdResponse.builder().id(productService.createProduct(storeId, request)).build());
    }

    // 需要鉴权 仅 Staff（本店）
    @PutMapping("/{productId}")
    public ApiResponse<Void> updateProduct(@PathVariable Long storeId,
                                            @PathVariable Long productId,
                                            @RequestBody UpdateProductRequest request) {
        productService.updateProduct(storeId, productId, request);
        return ApiResponse.success();
    }

    // 需要鉴权 仅 Staff（本店）
    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long storeId,
                                            @PathVariable Long productId) {
        productService.deleteProduct(storeId, productId);
        return ApiResponse.success();
    }

    // 需要鉴权 仅 Staff（本店）
    @PutMapping("/{productId}/stock")
    public ApiResponse<UpdateStockResponse> updateStock(@PathVariable Long storeId,
                                                         @PathVariable Long productId,
                                                         @RequestBody @Valid UpdateStockRequest request) {
        return ApiResponse.success(productService.updateStock(storeId, productId, request));
    }
}
