package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
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

public interface OrderService extends IService<Order> {

    /**
     * 从购物车选中条目创建订单。
     * 校验库存充足、地址属于当前用户、优惠券可用（若传入）；
     * 成功后减库存、核销优惠券、清除对应购物车条目。
     * 对应 POST /api/orders
     */
    CreateOrderResponse createOrder(Long userId, CreateOrderRequest request);

    /**
     * 分页获取当前用户的订单列表，可按状态筛选。
     * 对应 GET /api/orders
     */
    IPage<OrderListItemResponse> getMyOrders(Long userId, String status, int page, int size);

    /**
     * 获取订单详情（含地址快照、订单条目列表）。
     * Customer 只能查自己的订单，Staff 只能查本店订单，否则抛出权限异常。
     * 对应 GET /api/orders/{orderId}
     *
     * @param requestUserId 当前请求用户 ID
     * @param role          当前用户角色（CUSTOMER / STAFF / ADMIN）
     */
    OrderDetailResponse getOrderDetail(Long requestUserId, String role, Long orderId);

    /**
     * 模拟支付指定订单。校验订单属于当前用户且状态为 PENDING_PAYMENT。
     * 成功后将订单状态更新为 PAID 并记录支付时间。
     * 对应 POST /api/orders/{orderId}/pay
     */
    PayOrderResponse payOrder(Long userId, Long orderId);

    /**
     * 取消订单。
     * 未支付订单直接取消；已支付订单取消后执行退款并标记 refunded=true。
     * 对应 POST /api/orders/{orderId}/cancel
     */
    CancelOrderResponse cancelOrder(Long userId, Long orderId);

    /**
     * 确认收货。校验订单属于当前用户且状态为 SHIPPED，更新为 COMPLETED。
     * 对应 POST /api/orders/{orderId}/confirm
     */
    ConfirmOrderResponse confirmReceived(Long userId, Long orderId);

    /**
     * 申请退款。校验订单属于当前用户且状态为 COMPLETED，更新为 CANCELLED 并记录退款信息。
     * 对应 POST /api/orders/{orderId}/refund
     */
    RefundResponse refundOrder(Long userId, Long orderId, RefundRequest request);

    /**
     * 分页获取指定商店的订单列表，可按状态筛选。仅 Staff 可调用。
     * 对应 GET /api/stores/{storeId}/orders
     */
    IPage<OrderListItemResponse> getStoreOrders(Long storeId, String status, int page, int size);

    /**
     * 对已支付订单执行发货操作，记录快递单号和快递公司，状态更新为 SHIPPED。
     * 校验订单归属该商店且状态为 PAID。仅 Staff 可调用。
     * 对应 POST /api/stores/{storeId}/orders/{orderId}/ship
     */
    ShipOrderResponse shipOrder(Long storeId, Long orderId, ShipOrderRequest request);
}
