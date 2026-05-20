package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.CancelOrderResponse;
import com.twentyzhang.bluewhale.dto.ConfirmOrderResponse;
import com.twentyzhang.bluewhale.dto.CreateOrderRequest;
import com.twentyzhang.bluewhale.dto.CreateOrderResponse;
import com.twentyzhang.bluewhale.dto.OrderDetailResponse;
import com.twentyzhang.bluewhale.dto.OrderListItemResponse;
import com.twentyzhang.bluewhale.dto.PayOrderResponse;
import com.twentyzhang.bluewhale.dto.RefundRequest;
import com.twentyzhang.bluewhale.dto.RefundResponse;
import com.twentyzhang.bluewhale.dto.ShipOrderRequest;
import com.twentyzhang.bluewhale.dto.ShipOrderResponse;
import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.service.OrderService;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Override
    public CreateOrderResponse createOrder(Long userId, CreateOrderRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public IPage<OrderListItemResponse> getMyOrders(Long userId, String status, int page, int size) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public OrderDetailResponse getOrderDetail(Long requestUserId, String role, Long orderId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public PayOrderResponse payOrder(Long userId, Long orderId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public CancelOrderResponse cancelOrder(Long userId, Long orderId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public ConfirmOrderResponse confirmReceived(Long userId, Long orderId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public RefundResponse refundOrder(Long userId, Long orderId, RefundRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public IPage<OrderListItemResponse> getStoreOrders(Long storeId, String status, int page, int size) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public ShipOrderResponse shipOrder(Long storeId, Long orderId, ShipOrderRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
