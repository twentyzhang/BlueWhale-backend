package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.dto.AddCartItemRequest;
import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.CartResponse;
import com.twentyzhang.bluewhale.dto.UpdateCartItemRequest;
import com.twentyzhang.bluewhale.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // 需要鉴权 仅 Customer
    @GetMapping
    public ApiResponse<CartResponse> getCart() {
        return ApiResponse.success(cartService.getCart(getCurrentUserId()));
    }

    // 需要鉴权 仅 Customer
    @PostMapping("/items")
    public ApiResponse<Void> addItem(@RequestBody @Valid AddCartItemRequest request) {
        cartService.addItem(getCurrentUserId(), request);
        return ApiResponse.success();
    }

    // 需要鉴权 仅 Customer
    @PutMapping("/items/{itemId}")
    public ApiResponse<Void> updateItemQuantity(@PathVariable Long itemId,
                                                 @RequestBody @Valid UpdateCartItemRequest request) {
        cartService.updateItemQuantity(getCurrentUserId(), itemId, request);
        return ApiResponse.success();
    }

    // 需要鉴权 仅 Customer
    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Void> removeItem(@PathVariable Long itemId) {
        cartService.removeItem(getCurrentUserId(), itemId);
        return ApiResponse.success();
    }

    private Long getCurrentUserId() {
        // TODO: 从 JWT Token 解析 userId
        return null;
    }
}
