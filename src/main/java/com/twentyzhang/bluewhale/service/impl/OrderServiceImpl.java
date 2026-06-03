package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.common.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.twentyzhang.bluewhale.dto.CancelOrderResponse;
import com.twentyzhang.bluewhale.dto.ConfirmOrderResponse;
import com.twentyzhang.bluewhale.dto.CreateOrderRequest;
import com.twentyzhang.bluewhale.dto.CreateOrderResponse;
import com.twentyzhang.bluewhale.dto.OrderAddressResponse;
import com.twentyzhang.bluewhale.dto.OrderDetailResponse;
import com.twentyzhang.bluewhale.dto.OrderItemDetailResponse;
import com.twentyzhang.bluewhale.dto.OrderListItemResponse;
import com.twentyzhang.bluewhale.dto.PayOrderResponse;
import com.twentyzhang.bluewhale.dto.RefundRequest;
import com.twentyzhang.bluewhale.dto.RefundResponse;
import com.twentyzhang.bluewhale.dto.ShipOrderRequest;
import com.twentyzhang.bluewhale.dto.ShipOrderResponse;
import com.twentyzhang.bluewhale.entity.Cart;
import com.twentyzhang.bluewhale.entity.CartItem;
import com.twentyzhang.bluewhale.entity.Coupon;
import com.twentyzhang.bluewhale.entity.CouponGroup;
import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.entity.OrderItem;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.Store;
import com.twentyzhang.bluewhale.entity.UserAddress;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.CartItemMapper;
import com.twentyzhang.bluewhale.mapper.CartMapper;
import com.twentyzhang.bluewhale.mapper.CouponGroupMapper;
import com.twentyzhang.bluewhale.mapper.CouponMapper;
import com.twentyzhang.bluewhale.mapper.OrderItemMapper;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.mapper.StoreMapper;
import com.twentyzhang.bluewhale.mapper.UserAddressMapper;
import com.twentyzhang.bluewhale.service.OrderService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final CartMapper         cartMapper;
    private final CartItemMapper     cartItemMapper;
    private final UserAddressMapper  userAddressMapper;
    private final StoreMapper        storeMapper;
    private final ProductMapper      productMapper;
    private final CouponMapper       couponMapper;
    private final CouponGroupMapper  couponGroupMapper;
    private final OrderItemMapper    orderItemMapper;

    // ── createOrder ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CreateOrderResponse createOrder(Long userId, CreateOrderRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        // ─── Phase 1：全部校验（任何一步失败都不写库）────────────────────────

        // 1. 加载购物车条目，验证存在且属于当前用户
        List<CartItem> cartItems = cartItemMapper.selectBatchIds(request.getCartItemIds());
        if (cartItems.size() != request.getCartItemIds().size()) {
            throw new BusinessException("部分购物车条目不存在");
        }
        Cart cart = cartMapper.selectOne(
                new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));
        if (cart == null || cartItems.stream().anyMatch(i -> !cart.getId().equals(i.getCartId()))) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "购物车条目不属于当前用户");
        }

        // 2. 验证收货地址
        UserAddress address = userAddressMapper.selectById(request.getAddressId());
        if (address == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "收货地址不存在");
        }
        if (!userId.equals(address.getUserId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权使用该收货地址");
        }

        // 3. 批量加载商品，校验存在性与库存
        List<Long> productIds = cartItems.stream()
                .map(CartItem::getProductId).collect(Collectors.toList());
        Map<Long, Product> productMap = new HashMap<>();
        productMapper.selectBatchIds(productIds).forEach(p -> productMap.put(p.getId(), p));

        for (CartItem item : cartItems) {
            Product product = productMap.get(item.getProductId());
            if (product == null) {
                throw new BusinessException("商品 ID=" + item.getProductId() + " 不存在或已下架");
            }
            if (product.getStock() < item.getQuantity()) {
                throw new BusinessException(
                        "商品【" + product.getName() + "】库存不足，当前库存 "
                        + product.getStock() + "，需要 " + item.getQuantity());
            }
        }

        // 4. 验证优惠券（如有）
        Coupon coupon = null;
        CouponGroup couponGroup = null;
        if (request.getCouponId() != null) {
            coupon = couponMapper.selectById(request.getCouponId());
            if (coupon == null || !userId.equals(coupon.getUserId())) {
                throw new BusinessException(Result.CODE_NOT_FOUND, "优惠券不存在");
            }
            if (!"UNUSED".equals(coupon.getStatus())) {
                throw new BusinessException("优惠券不可用，当前状态：" + coupon.getStatus());
            }
            couponGroup = couponGroupMapper.selectById(coupon.getGroupId());
            if (couponGroup == null
                    || (couponGroup.getExpireAt() != null
                            && couponGroup.getExpireAt().isBefore(LocalDateTime.now()))) {
                throw new BusinessException("优惠券已过期");
            }
        }

        // 5. 计算金额
        BigDecimal totalAmount = cartItems.stream()
                .map(item -> productMap.get(item.getProductId()).getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (couponGroup != null) {
            if ("AMOUNT_OFF".equals(couponGroup.getType())) {
                // 满减券：检查最低使用金额门槛
                if (couponGroup.getMinOrderAmount() != null
                        && couponGroup.getMinOrderAmount().compareTo(BigDecimal.ZERO) > 0
                        && totalAmount.compareTo(couponGroup.getMinOrderAmount()) < 0) {
                    throw new BusinessException(
                            "订单金额未达优惠券最低使用金额 " + couponGroup.getMinOrderAmount() + " 元");
                }
                discountAmount = couponGroup.getValue();
            } else if ("DISCOUNT".equals(couponGroup.getType())) {
                // 折扣券：discountAmount = totalAmount × (1 - value)
                discountAmount = totalAmount
                        .multiply(BigDecimal.ONE.subtract(couponGroup.getValue()))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal payableAmount = totalAmount.subtract(discountAmount);
        // payableAmount 最小 0.01，不能为负
        if (payableAmount.compareTo(new BigDecimal("0.01")) < 0) {
            payableAmount = new BigDecimal("0.01");
        }

        // storeId 从商品中取（同一购物车下的商品应属同一店铺）
        Long storeId = productMap.values().iterator().next().getStoreId();

        // ─── Phase 2：所有写操作（全部校验通过后统一执行）──────────────────

        // 6. 统一扣减库存（所有校验已通过，现在才操作 DB）
        for (CartItem item : cartItems) {
            Product product = productMap.get(item.getProductId());
            product.setStock(product.getStock() - item.getQuantity());
            productMapper.updateById(product);
        }

        // 7. 创建订单
        Order order = Order.builder()
                .userId(userId)
                .storeId(storeId)
                .status("PENDING_PAYMENT")
                .totalAmount(totalAmount)
                .discountAmount(discountAmount)
                .payableAmount(payableAmount)
                .couponId(request.getCouponId())
                .addrReceiverName(address.getReceiverName())
                .addrPhone(address.getPhone())
                .addrProvince(address.getProvince())
                .addrCity(address.getCity())
                .addrDistrict(address.getDistrict())
                .addrDetail(address.getDetail())
                .build();
        save(order); // 自增 ID 回写到 order.id

        // 8. 创建订单条目（冗余存储商品名称和单价快照）
        for (CartItem item : cartItems) {
            Product product = productMap.get(item.getProductId());
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            orderItemMapper.insert(OrderItem.builder()
                    .orderId(order.getId())
                    .productId(item.getProductId())
                    .productName(product.getName())
                    .productImage(product.getImageUrl())
                    .unitPrice(product.getPrice())
                    .quantity(item.getQuantity())
                    .subtotal(subtotal)
                    .build());
        }

        // 9. 核销优惠券
        if (coupon != null) {
            coupon.setStatus("USED");
            coupon.setUsedAt(LocalDateTime.now());
            coupon.setOrderId(order.getId());
            couponMapper.updateById(coupon);
        }

        // 10. 从购物车删除已结算条目
        request.getCartItemIds().forEach(cartItemMapper::deleteById);

        return CreateOrderResponse.builder()
                .orderId(order.getId())
                .totalAmount(totalAmount)
                .discountAmount(discountAmount)
                .payableAmount(payableAmount)
                .build();
    }

    // ── getMyOrders ───────────────────────────────────────────────────────────

    @Override
    public IPage<OrderListItemResponse> getMyOrders(Long userId, String status, int page, int size) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime);

        return toListResponse(this.page(new Page<>(page, size), wrapper));
    }

    // ── getOrderDetail ────────────────────────────────────────────────────────

    @Override
    public OrderDetailResponse getOrderDetail(Long requestUserId, String role, Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "订单不存在");
        }

        // 权限校验：Customer 只能查自己的，Staff 只能查本店的，Admin 不限
        if (AuthUtil.ROLE_CUSTOMER.equals(role)) {
            if (!requestUserId.equals(order.getUserId())) {
                throw new BusinessException(Result.CODE_FORBIDDEN, "无权查看该订单");
            }
        } else if (AuthUtil.ROLE_STAFF.equals(role)) {
            Long staffStoreId = AuthUtil.getCurrentUser().storeId();
            if (!staffStoreId.equals(order.getStoreId())) {
                throw new BusinessException(Result.CODE_FORBIDDEN, "无权查看该订单");
            }
        }

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));

        List<OrderItemDetailResponse> itemResponses = items.stream()
                .map(i -> OrderItemDetailResponse.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .price(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .subtotal(i.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        // 拼接地址快照：省 + 市 + 区 + 详细
        String addrDetail = order.getAddrProvince() + order.getAddrCity()
                + order.getAddrDistrict() + order.getAddrDetail();

        return OrderDetailResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .discountAmount(order.getDiscountAmount())
                .payableAmount(order.getPayableAmount())
                .createdAt(order.getCreateTime())
                .paidAt(order.getPaidAt())
                .address(OrderAddressResponse.builder()
                        .receiverName(order.getAddrReceiverName())
                        .phone(order.getAddrPhone())
                        .detail(addrDetail)
                        .build())
                .items(itemResponses)
                .build();
    }

    // ── payOrder ──────────────────────────────────────────────────────────────

    @Override
    public PayOrderResponse payOrder(Long userId, Long orderId) {
        Order order = requireCustomerOrder(userId, orderId);

        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException(
                    "订单无法支付，当前状态：" + order.getStatus() + "（需为 PENDING_PAYMENT）");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus("PAID");
        order.setPaidAt(now);
        updateById(order);

        return PayOrderResponse.builder()
                .status("PAID")
                .paidAt(now)
                .build();
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CancelOrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = requireCustomerOrder(userId, orderId);

        String currentStatus = order.getStatus();
        if (!"PENDING_PAYMENT".equals(currentStatus) && !"PAID".equals(currentStatus)) {
            throw new BusinessException(
                    "订单无法取消，当前状态：" + currentStatus + "（需为 PENDING_PAYMENT 或 PAID）");
        }

        // 下单时库存已扣减，取消时无论是否已支付都需恢复
        restoreInventory(orderId);

        // 若使用了优惠券，恢复为 UNUSED
        restoreCoupon(order.getCouponId());

        order.setStatus("CANCELLED");
        order.setCancelledAt(LocalDateTime.now());
        updateById(order);

        boolean refunded = "PAID".equals(currentStatus);
        return CancelOrderResponse.builder()
                .status("CANCELLED")
                .refunded(refunded)
                .build();
    }

    // ── confirmReceived ───────────────────────────────────────────────────────

    @Override
    public ConfirmOrderResponse confirmReceived(Long userId, Long orderId) {
        Order order = requireCustomerOrder(userId, orderId);

        if (!"SHIPPED".equals(order.getStatus())) {
            throw new BusinessException(
                    "订单无法确认收货，当前状态：" + order.getStatus() + "（需为 SHIPPED）");
        }

        order.setStatus("COMPLETED");
        order.setCompletedAt(LocalDateTime.now());
        updateById(order);

        return ConfirmOrderResponse.builder().status("COMPLETED").build();
    }

    // ── refundOrder ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public RefundResponse refundOrder(Long userId, Long orderId, RefundRequest request) {
        Order order = requireCustomerOrder(userId, orderId);

        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(
                    "订单无法申请退款，当前状态：" + order.getStatus() + "（需为 COMPLETED）");
        }

        restoreInventory(orderId);
        restoreCoupon(order.getCouponId());

        LocalDateTime now = LocalDateTime.now();
        order.setStatus("CANCELLED");
        order.setCancelledAt(now);
        updateById(order);

        return RefundResponse.builder()
                .status("CANCELLED")
                .refundedAt(now)
                .build();
    }

    // ── getStoreOrders ────────────────────────────────────────────────────────

    @Override
    public IPage<OrderListItemResponse> getStoreOrders(Long storeId, String status, int page, int size) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);
        AuthUtil.requireStoreAccess(storeId);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getStoreId, storeId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreateTime);

        return toListResponse(this.page(new Page<>(page, size), wrapper));
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

    /**
     * 将订单分页结果转为列表响应 VO。
     * 采用二次批量查询：一次查商店名，一次批量查条目数（决策文档第 16 条）。
     */
    private IPage<OrderListItemResponse> toListResponse(IPage<Order> orderPage) {
        List<Order> orders = orderPage.getRecords();

        if (orders.isEmpty()) {
            return orderPage.convert(o -> OrderListItemResponse.builder().build());
        }

        // 批量查商店名
        List<Long> storeIds = orders.stream()
                .map(Order::getStoreId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
        Map<Long, String> storeNameMap = new HashMap<>();
        storeMapper.selectBatchIds(storeIds)
                .forEach(s -> storeNameMap.put(s.getId(), s.getName()));

        // 批量查每笔订单的条目数（按 orderId 聚合）
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        Map<Long, Long> itemCountMap = orderItemMapper
                .selectList(new LambdaQueryWrapper<OrderItem>()
                        .select(OrderItem::getOrderId)
                        .in(OrderItem::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId, Collectors.counting()));

        return orderPage.convert(o -> OrderListItemResponse.builder()
                .id(o.getId())
                .status(o.getStatus())
                .payableAmount(o.getPayableAmount())
                .createdAt(o.getCreateTime())
                .storeName(storeNameMap.get(o.getStoreId()))
                .itemCount(itemCountMap.getOrDefault(o.getId(), 0L).intValue())
                .build());
    }

    // ── shipOrder ─────────────────────────────────────────────────────────────

    @Override
    public ShipOrderResponse shipOrder(Long storeId, Long orderId, ShipOrderRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);
        AuthUtil.requireStoreAccess(storeId);

        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "订单不存在");
        }
        if (!storeId.equals(order.getStoreId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权操作该订单");
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(
                    "订单无法发货，当前状态：" + order.getStatus() + "（需为 PAID）");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus("SHIPPED");
        order.setShippedAt(now);
        order.setTrackingNumber(request.getTrackingNumber());
        order.setCarrier(request.getCarrier());
        updateById(order);

        return ShipOrderResponse.builder()
                .status("SHIPPED")
                .shippedAt(now)
                .build();
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

    /** 加载订单，校验存在性和 Customer 归属。 */
    private Order requireCustomerOrder(Long userId, Long orderId) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);
        Order order = getById(orderId);
        if (order == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "订单不存在");
        }
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权操作该订单");
        }
        return order;
    }

    /** 恢复该订单所有条目对应商品的库存。逻辑删除的商品跳过，不影响取消流程。 */
    private void restoreInventory(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            Product product = productMapper.selectById(item.getProductId());
            if (product != null) {
                product.setStock(product.getStock() + item.getQuantity());
                productMapper.updateById(product);
            }
        }
    }

    /**
     * 将指定优惠券恢复为 UNUSED 状态并清空使用记录。
     * 使用 UpdateWrapper 以支持显式 SET NULL（updateById 默认跳过 null 字段）。
     */
    private void restoreCoupon(Long couponId) {
        if (couponId == null) return;
        couponMapper.update(null, new UpdateWrapper<Coupon>()
                .eq("id", couponId)
                .set("status", "UNUSED")
                .set("used_at", null)
                .set("order_id", null));
    }
}
