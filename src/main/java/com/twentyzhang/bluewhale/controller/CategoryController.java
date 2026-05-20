package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.CategoryNodeResponse;
import com.twentyzhang.bluewhale.dto.CreateCategoryRequest;
import com.twentyzhang.bluewhale.dto.IdResponse;
import com.twentyzhang.bluewhale.service.ProductCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ProductCategoryService productCategoryService;

    @GetMapping
    public ApiResponse<List<CategoryNodeResponse>> getCategoryTree() {
        return ApiResponse.success(productCategoryService.getCategoryTree());
    }

    // 需要鉴权 仅 Staff 或 Admin
    @PostMapping
    public ApiResponse<IdResponse> createCategory(@RequestBody @Valid CreateCategoryRequest request) {
        return ApiResponse.success(IdResponse.builder().id(productCategoryService.createCategory(request)).build());
    }

    // 需要鉴权 仅 Admin
    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long categoryId) {
        productCategoryService.deleteCategory(categoryId);
        return ApiResponse.success();
    }
}
