package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.AddCartItemRequest;
import com.twentyzhang.bluewhale.dto.CartResponse;
import com.twentyzhang.bluewhale.dto.UpdateCartItemRequest;
import com.twentyzhang.bluewhale.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public Result<CartResponse> getCart() {
        return Result.success(cartService.getCart(currentUser().userId()));
    }

    // 需要鉴权 仅 Customer
    @PostMapping("/items")
    public Result<Void> addItem(@RequestBody @Valid AddCartItemRequest request) {
        cartService.addItem(currentUser().userId(), request);
        return Result.success();
    }

    // 需要鉴权 仅 Customer
    @PutMapping("/items/{itemId}")
    public Result<Void> updateItemQuantity(@PathVariable Long itemId,
                                           @RequestBody @Valid UpdateCartItemRequest request) {
        cartService.updateItemQuantity(currentUser().userId(), itemId, request);
        return Result.success();
    }

    // 需要鉴权 仅 Customer
    @DeleteMapping("/items/{itemId}")
    public Result<Void> removeItem(@PathVariable Long itemId) {
        cartService.removeItem(currentUser().userId(), itemId);
        return Result.success();
    }

    private AuthUser currentUser() {
        return (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
