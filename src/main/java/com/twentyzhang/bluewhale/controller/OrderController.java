package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
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
import org.springframework.security.core.context.SecurityContextHolder;
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
    public Result<CreateOrderResponse> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        return Result.success(orderService.createOrder(currentUser().userId(), request));
    }

    // 需要鉴权 仅 Customer
    @GetMapping
    public Result<IPage<OrderListItemResponse>> getMyOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.getMyOrders(currentUser().userId(), status, page, size));
    }

    // 需要鉴权（Customer 查自己的，Staff 查本店的）
    @GetMapping("/{orderId}")
    public Result<OrderDetailResponse> getOrderDetail(@PathVariable Long orderId) {
        AuthUser user = currentUser();
        return Result.success(orderService.getOrderDetail(user.userId(), user.role(), orderId));
    }

    // 需要鉴权 仅 Customer（本人订单）
    @PostMapping("/{orderId}/pay")
    public Result<PayOrderResponse> payOrder(@PathVariable Long orderId) {
        return Result.success(orderService.payOrder(currentUser().userId(), orderId));
    }

    // 需要鉴权 仅 Customer（本人订单）
    @PostMapping("/{orderId}/cancel")
    public Result<CancelOrderResponse> cancelOrder(@PathVariable Long orderId) {
        return Result.success(orderService.cancelOrder(currentUser().userId(), orderId));
    }

    // 需要鉴权 仅 Customer（本人订单）
    @PostMapping("/{orderId}/confirm")
    public Result<ConfirmOrderResponse> confirmReceived(@PathVariable Long orderId) {
        return Result.success(orderService.confirmReceived(currentUser().userId(), orderId));
    }

    // 需要鉴权 仅 Customer（本人订单）
    @PostMapping("/{orderId}/refund")
    public Result<RefundResponse> refundOrder(@PathVariable Long orderId,
                                               @RequestBody @Valid RefundRequest request) {
        return Result.success(orderService.refundOrder(currentUser().userId(), orderId, request));
    }

    private AuthUser currentUser() {
        return (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
