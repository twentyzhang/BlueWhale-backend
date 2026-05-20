package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.CancelOrderResponse;
import com.twentyzhang.bluewhale.dto.ConfirmOrderResponse;
import com.twentyzhang.bluewhale.dto.CreateOrderRequest;
import com.twentyzhang.bluewhale.dto.CreateOrderResponse;
import com.twentyzhang.bluewhale.dto.OrderDetailResponse;
import com.twentyzhang.bluewhale.dto.OrderListItemResponse;
import com.twentyzhang.bluewhale.dto.PayOrderResponse;
import com.twentyzhang.bluewhale.dto.RefundRequest;
import com.twentyzhang.bluewhale.dto.RefundResponse;
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
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 需要鉴权 仅 Customer
    @PostMapping
    public ApiResponse<CreateOrderResponse> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        return ApiResponse.success(orderService.createOrder(getCurrentUserId(), request));
    }

    // 需要鉴权 仅 Customer
    @GetMapping
    public ApiResponse<IPage<OrderListItemResponse>> getMyOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(orderService.getMyOrders(getCurrentUserId(), status, page, size));
    }

    // 需要鉴权（Customer 查自己的，Staff 查本店的）
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.getOrderDetail(getCurrentUserId(), getCurrentUserRole(), orderId));
    }

    // 需要鉴权 仅 Customer（本人订单）
    @PostMapping("/{orderId}/pay")
    public ApiResponse<PayOrderResponse> payOrder(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.payOrder(getCurrentUserId(), orderId));
    }

    // 需要鉴权 仅 Customer（本人订单）
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<CancelOrderResponse> cancelOrder(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.cancelOrder(getCurrentUserId(), orderId));
    }

    // 需要鉴权 仅 Customer（本人订单）
    @PostMapping("/{orderId}/confirm")
    public ApiResponse<ConfirmOrderResponse> confirmReceived(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.confirmReceived(getCurrentUserId(), orderId));
    }

    // 需要鉴权 仅 Customer（本人订单）
    @PostMapping("/{orderId}/refund")
    public ApiResponse<RefundResponse> refundOrder(@PathVariable Long orderId,
                                                    @RequestBody @Valid RefundRequest request) {
        return ApiResponse.success(orderService.refundOrder(getCurrentUserId(), orderId, request));
    }

    private Long getCurrentUserId() {
        // TODO: 从 JWT Token 解析 userId
        return null;
    }

    private String getCurrentUserRole() {
        // TODO: 从 JWT Token 解析 role
        return null;
    }
}
