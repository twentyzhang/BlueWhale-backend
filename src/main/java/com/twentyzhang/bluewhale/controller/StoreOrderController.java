package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.OrderListItemResponse;
import com.twentyzhang.bluewhale.dto.ShipOrderRequest;
import com.twentyzhang.bluewhale.dto.ShipOrderResponse;
import com.twentyzhang.bluewhale.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores/{storeId}/orders")
@RequiredArgsConstructor
public class StoreOrderController {

    private final OrderService orderService;

    // 需要鉴权 仅 Staff（本店）
    @GetMapping
    public ApiResponse<IPage<OrderListItemResponse>> getStoreOrders(
            @PathVariable Long storeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(orderService.getStoreOrders(storeId, status, page, size));
    }

    // 需要鉴权 仅 Staff（本店，订单状态为 PAID）
    @PostMapping("/{orderId}/ship")
    public ApiResponse<ShipOrderResponse> shipOrder(@PathVariable Long storeId,
                                                     @PathVariable Long orderId,
                                                     @RequestBody @Valid ShipOrderRequest request) {
        return ApiResponse.success(orderService.shipOrder(storeId, orderId, request));
    }
}
