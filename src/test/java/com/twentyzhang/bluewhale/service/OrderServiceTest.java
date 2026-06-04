package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.*;
import com.twentyzhang.bluewhale.entity.*;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.*;
import com.twentyzhang.bluewhale.service.impl.OrderServiceImpl;
import com.twentyzhang.bluewhale.util.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OrderService")
class OrderServiceTest extends BaseServiceTest {

    // OrderMapper 作为 baseMapper 单独处理，其余通过 @RequiredArgsConstructor 注入
    @Mock private OrderMapper      orderMapper;
    @Mock private CartMapper       cartMapper;
    @Mock private CartItemMapper   cartItemMapper;
    @Mock private UserAddressMapper userAddressMapper;
    @Mock private StoreMapper      storeMapper;
    @Mock private ProductMapper    productMapper;
    @Mock private CouponMapper     couponMapper;
    @Mock private CouponGroupMapper couponGroupMapper;
    @Mock private OrderItemMapper  orderItemMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    // disambiguation helpers（BaseMapper 3.5.9 insert/updateById 重载）
    private static Order     anyOrder()     { return ArgumentMatchers.any(Order.class); }
    private static OrderItem anyOrderItem() { return ArgumentMatchers.any(OrderItem.class); }
    private static Product   anyProduct()   { return ArgumentMatchers.any(Product.class); }
    private static Coupon    anyCoupon()    { return ArgumentMatchers.any(Coupon.class); }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
    }

    // ── 构建辅助 ──────────────────────────────────────────────────────────────

    private static CartItem cartItem(Long id, Long cartId, Long productId, int qty) {
        return CartItem.builder().id(id).cartId(cartId).productId(productId).quantity(qty).build();
    }

    private static Product product(Long id, String name, BigDecimal price, int stock, Long storeId) {
        return Product.builder().id(id).name(name).price(price).stock(stock).storeId(storeId).build();
    }

    private static UserAddress address(Long id, Long userId) {
        return UserAddress.builder().id(id).userId(userId)
                .receiverName("张三").phone("13800138000")
                .province("江苏省").city("南京市").district("鼓楼区").detail("汉口路22号")
                .build();
    }

    private static Order order(Long id, Long userId, Long storeId, String status) {
        return Order.builder().id(id).userId(userId).storeId(storeId).status(status).build();
    }

    private static Order orderWithCoupon(Long id, Long userId, Long storeId,
                                          String status, Long couponId) {
        return Order.builder().id(id).userId(userId).storeId(storeId)
                .status(status).couponId(couponId).build();
    }

    private static CreateOrderRequest orderReq(List<Long> cartItemIds, Long addressId,
                                                Long couponId) {
        CreateOrderRequest r = new CreateOrderRequest();
        r.setCartItemIds(cartItemIds);
        r.setAddressId(addressId);
        r.setCouponId(couponId);
        return r;
    }

    private static ShipOrderRequest shipReq(String trackingNumber, String carrier) {
        ShipOrderRequest r = new ShipOrderRequest();
        r.setTrackingNumber(trackingNumber);
        r.setCarrier(carrier);
        return r;
    }

    private static RefundRequest refundReq() {
        RefundRequest r = new RefundRequest();
        r.setReason("质量问题");
        return r;
    }

    // ── createOrder ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createOrder")
    class CreateOrderTests {

        /** 为正常下单流程设置公共 stub（购物车、地址、商品）。 */
        private void stubBasicFlow(List<CartItem> items, List<Product> products) {
            when(cartItemMapper.selectBatchIds(any())).thenReturn(items);
            when(cartMapper.selectOne(any())).thenReturn(Cart.builder().id(10L).userId(1L).build());
            when(userAddressMapper.selectById(3L)).thenReturn(address(3L, 1L));
            when(productMapper.selectBatchIds(any())).thenReturn(products);
            // 乐观锁扣减库存：按 id 重新读取 + 带版本号条件更新（返回 1 表示成功）
            for (Product p : products) {
                when(productMapper.selectById(p.getId())).thenReturn(p);
            }
            when(productMapper.updateStockWithVersion(any(), anyInt(), any())).thenReturn(1);
            doAnswer(inv -> { ((Order) inv.getArgument(0)).setId(1001L); return 1; })
                    .when(orderMapper).insert(anyOrder());
            when(orderItemMapper.insert(anyOrderItem())).thenReturn(1);
        }

        @Test
        @DisplayName("正常创建订单（不使用优惠券），验证金额计算、库存扣减、购物车条目删除")
        void success_noCode_verifySideEffects() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            stubBasicFlow(
                    List.of(cartItem(1L, 10L, 101L, 2),
                            cartItem(2L, 10L, 102L, 1)),
                    List.of(product(101L, "商品A", new BigDecimal("10.00"), 50, 100L),
                            product(102L, "商品B", new BigDecimal("20.00"), 30, 100L))
            );

            // 2*10 + 1*20 = 40
            CreateOrderResponse resp = orderService.createOrder(1L, orderReq(List.of(1L, 2L), 3L, null));

            assertEquals(new BigDecimal("40.00"), resp.getTotalAmount());
            assertEquals(BigDecimal.ZERO,          resp.getDiscountAmount());
            assertEquals(new BigDecimal("40.00"), resp.getPayableAmount());
            assertEquals(1001L, resp.getOrderId());

            // 库存乐观锁扣减：按数量调用 updateStockWithVersion
            verify(productMapper).updateStockWithVersion(eq(101L), eq(2), any());
            verify(productMapper).updateStockWithVersion(eq(102L), eq(1), any());

            // 购物车条目删除
            verify(cartItemMapper, times(2)).deleteById((Serializable) any());
        }

        @Test
        @DisplayName("使用 AMOUNT_OFF 优惠券（满30减5），验证折扣金额计算正确")
        void success_amountOffCoupon() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            // 3 * 15 = 45，满30减5 → payable = 40
            stubBasicFlow(
                    List.of(cartItem(1L, 10L, 101L, 3)),
                    List.of(product(101L, "商品A", new BigDecimal("15.00"), 10, 100L))
            );
            when(couponMapper.selectById(5L)).thenReturn(
                    Coupon.builder().id(5L).userId(1L).groupId(20L).status("UNUSED").build());
            when(couponGroupMapper.selectById(20L)).thenReturn(
                    CouponGroup.builder().id(20L).type("AMOUNT_OFF")
                            .value(new BigDecimal("5.00"))
                            .minOrderAmount(new BigDecimal("30.00")).build());
            when(couponMapper.updateById(anyCoupon())).thenReturn(1);

            CreateOrderResponse resp = orderService.createOrder(1L, orderReq(List.of(1L), 3L, 5L));

            assertEquals(new BigDecimal("45.00"), resp.getTotalAmount());
            assertEquals(new BigDecimal("5.00"),  resp.getDiscountAmount());
            assertEquals(new BigDecimal("40.00"), resp.getPayableAmount());
        }

        @Test
        @DisplayName("使用 DISCOUNT 优惠券（8折），验证折扣金额计算正确")
        void success_discountCoupon() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            // 2 * 20 = 40，8折 → discount = 40 × (1-0.8) = 8，payable = 32
            stubBasicFlow(
                    List.of(cartItem(1L, 10L, 101L, 2)),
                    List.of(product(101L, "商品A", new BigDecimal("20.00"), 10, 100L))
            );
            when(couponMapper.selectById(5L)).thenReturn(
                    Coupon.builder().id(5L).userId(1L).groupId(20L).status("UNUSED").build());
            when(couponGroupMapper.selectById(20L)).thenReturn(
                    CouponGroup.builder().id(20L).type("DISCOUNT")
                            .value(new BigDecimal("0.80"))
                            .minOrderAmount(BigDecimal.ZERO).build());
            when(couponMapper.updateById(anyCoupon())).thenReturn(1);

            CreateOrderResponse resp = orderService.createOrder(1L, orderReq(List.of(1L), 3L, 5L));

            assertEquals(new BigDecimal("40.00"), resp.getTotalAmount());
            assertEquals(new BigDecimal("8.00"),  resp.getDiscountAmount());
            assertEquals(new BigDecimal("32.00"), resp.getPayableAmount());
        }

        @Test
        @DisplayName("商品库存不足时抛出 BusinessException，提示包含商品名称")
        void insufficientStock_throwsWithProductName() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectBatchIds(any())).thenReturn(
                    List.of(cartItem(1L, 10L, 101L, 5)));    // 需要 5
            when(cartMapper.selectOne(any())).thenReturn(Cart.builder().id(10L).userId(1L).build());
            when(userAddressMapper.selectById(3L)).thenReturn(address(3L, 1L));
            when(productMapper.selectBatchIds(any())).thenReturn(
                    List.of(product(101L, "老陈醋", new BigDecimal("10.00"), 3, 100L))); // 库存只有 3

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(1L, orderReq(List.of(1L), 3L, null)));

            assertTrue(ex.getMessage().contains("老陈醋"), "错误消息应包含商品名称，实际：" + ex.getMessage());
            verify(productMapper, never()).updateStockWithVersion(any(), anyInt(), any()); // 校验失败不写库
        }

        @Test
        @DisplayName("地址不存在时抛出 BusinessException（code 404）")
        void addressNotFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectBatchIds(any())).thenReturn(
                    List.of(cartItem(1L, 10L, 101L, 1)));
            when(cartMapper.selectOne(any())).thenReturn(Cart.builder().id(10L).userId(1L).build());
            when(userAddressMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(1L, orderReq(List.of(1L), 999L, null)));

            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            // 地址校验失败不继续查商品
            verify(productMapper, never()).selectBatchIds(any());
        }

        @Test
        @DisplayName("优惠券已使用时抛出 BusinessException")
        void couponAlreadyUsed_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectBatchIds(any())).thenReturn(
                    List.of(cartItem(1L, 10L, 101L, 1)));
            when(cartMapper.selectOne(any())).thenReturn(Cart.builder().id(10L).userId(1L).build());
            when(userAddressMapper.selectById(3L)).thenReturn(address(3L, 1L));
            when(productMapper.selectBatchIds(any())).thenReturn(
                    List.of(product(101L, "商品A", new BigDecimal("10.00"), 10, 100L)));
            when(couponMapper.selectById(5L)).thenReturn(
                    Coupon.builder().id(5L).userId(1L).groupId(20L).status("USED").build());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(1L, orderReq(List.of(1L), 3L, 5L)));

            assertTrue(ex.getMessage().contains("USED"));
            verify(orderMapper, never()).insert(anyOrder());
        }

        @Test
        @DisplayName("订单金额未达到优惠券 minOrderAmount 时抛出 BusinessException")
        void amountBelowMinOrderAmount_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            // 1 * 15 = 15，低于门槛 50
            when(cartItemMapper.selectBatchIds(any())).thenReturn(
                    List.of(cartItem(1L, 10L, 101L, 1)));
            when(cartMapper.selectOne(any())).thenReturn(Cart.builder().id(10L).userId(1L).build());
            when(userAddressMapper.selectById(3L)).thenReturn(address(3L, 1L));
            when(productMapper.selectBatchIds(any())).thenReturn(
                    List.of(product(101L, "商品A", new BigDecimal("15.00"), 10, 100L)));
            when(couponMapper.selectById(5L)).thenReturn(
                    Coupon.builder().id(5L).userId(1L).groupId(20L).status("UNUSED").build());
            when(couponGroupMapper.selectById(20L)).thenReturn(
                    CouponGroup.builder().id(20L).type("AMOUNT_OFF")
                            .value(new BigDecimal("10.00"))
                            .minOrderAmount(new BigDecimal("50.00")).build());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(1L, orderReq(List.of(1L), 3L, 5L)));

            assertTrue(ex.getMessage().contains("50"), "错误消息应含门槛金额，实际：" + ex.getMessage());
            verify(orderMapper, never()).insert(anyOrder());
        }

        @Test
        @DisplayName("库存乐观锁冲突：第 1 次更新影响 0 行，重新读取后第 2 次成功")
        void optimisticLock_retrySucceeds() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectBatchIds(any())).thenReturn(List.of(cartItem(1L, 10L, 101L, 2)));
            when(cartMapper.selectOne(any())).thenReturn(Cart.builder().id(10L).userId(1L).build());
            when(userAddressMapper.selectById(3L)).thenReturn(address(3L, 1L));
            Product p = product(101L, "商品A", new BigDecimal("10.00"), 50, 100L);
            when(productMapper.selectBatchIds(any())).thenReturn(List.of(p));
            when(productMapper.selectById(101L)).thenReturn(p);
            // 第 1 次冲突（0 行），第 2 次成功（1 行）
            when(productMapper.updateStockWithVersion(eq(101L), eq(2), any())).thenReturn(0, 1);
            doAnswer(inv -> { ((Order) inv.getArgument(0)).setId(1001L); return 1; })
                    .when(orderMapper).insert(anyOrder());
            when(orderItemMapper.insert(anyOrderItem())).thenReturn(1);

            CreateOrderResponse resp = orderService.createOrder(1L, orderReq(List.of(1L), 3L, null));

            assertEquals(1001L, resp.getOrderId());
            verify(productMapper, times(2)).updateStockWithVersion(eq(101L), eq(2), any());
            verify(productMapper, times(2)).selectById(101L); // 每次重试前重新读取
        }

        @Test
        @DisplayName("库存乐观锁连续 3 次冲突后抛出『库存更新失败，请重试』，不创建订单")
        void optimisticLock_exhaustedThrows() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectBatchIds(any())).thenReturn(List.of(cartItem(1L, 10L, 101L, 2)));
            when(cartMapper.selectOne(any())).thenReturn(Cart.builder().id(10L).userId(1L).build());
            when(userAddressMapper.selectById(3L)).thenReturn(address(3L, 1L));
            Product p = product(101L, "商品A", new BigDecimal("10.00"), 50, 100L);
            when(productMapper.selectBatchIds(any())).thenReturn(List.of(p));
            when(productMapper.selectById(101L)).thenReturn(p);
            when(productMapper.updateStockWithVersion(eq(101L), eq(2), any())).thenReturn(0); // 始终冲突

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.createOrder(1L, orderReq(List.of(1L), 3L, null)));

            assertTrue(ex.getMessage().contains("库存更新失败"), "实际：" + ex.getMessage());
            verify(productMapper, times(3)).updateStockWithVersion(eq(101L), eq(2), any()); // 重试 3 次
            verify(orderMapper, never()).insert(anyOrder());
        }
    }

    // ── getOrderDetail ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrderDetail")
    class GetOrderDetailTests {

        private final Order sampleOrder = Order.builder()
                .id(1001L).userId(1L).storeId(100L).status("PAID")
                .totalAmount(new BigDecimal("40.00"))
                .discountAmount(BigDecimal.ZERO)
                .payableAmount(new BigDecimal("40.00"))
                .addrProvince("江苏省").addrCity("南京市").addrDistrict("鼓楼区").addrDetail("汉口路22号")
                .addrReceiverName("张三").addrPhone("13800138000")
                .build();

        @Test
        @DisplayName("Customer 正常查询自己的订单")
        void customer_success() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L)).thenReturn(sampleOrder);
            when(orderItemMapper.selectList(any())).thenReturn(List.of());

            OrderDetailResponse resp = orderService.getOrderDetail(1L, AuthUtil.ROLE_CUSTOMER, 1001L);

            assertEquals(1001L, resp.getId());
            assertEquals("PAID", resp.getStatus());
            assertEquals("张三", resp.getAddress().getReceiverName());
            assertTrue(resp.getAddress().getDetail().contains("汉口路22号"));
        }

        @Test
        @DisplayName("Customer 查询他人订单时抛出 code=403")
        void customer_otherOrder_throws403() {
            mockAuthUser(2L, AuthUtil.ROLE_CUSTOMER, null); // user=2，order.userId=1
            when(orderMapper.selectById(1001L)).thenReturn(sampleOrder);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.getOrderDetail(2L, AuthUtil.ROLE_CUSTOMER, 1001L));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verify(orderItemMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("Staff 查询本店订单")
        void staff_ownStore_success() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L); // storeId=100 与 order.storeId 匹配
            when(orderMapper.selectById(1001L)).thenReturn(sampleOrder);
            when(orderItemMapper.selectList(any())).thenReturn(List.of());

            OrderDetailResponse resp = orderService.getOrderDetail(10L, AuthUtil.ROLE_STAFF, 1001L);
            assertEquals(1001L, resp.getId());
        }

        @Test
        @DisplayName("Staff 查询其他店铺订单时抛出 code=403")
        void staff_otherStore_throws403() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 999L); // storeId=999，order.storeId=100
            when(orderMapper.selectById(1001L)).thenReturn(sampleOrder);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.getOrderDetail(10L, AuthUtil.ROLE_STAFF, 1001L));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
        }

        @Test
        @DisplayName("订单不存在时抛出 code=404")
        void notFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(9999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.getOrderDetail(1L, AuthUtil.ROLE_CUSTOMER, 9999L));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
        }
    }

    // ── payOrder ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("payOrder")
    class PayOrderTests {

        @Test
        @DisplayName("正常支付，验证状态变更为 PAID 且 paidAt 被记录")
        void success_statusAndPaidAt() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "PENDING_PAYMENT"));
            when(orderMapper.updateById(anyOrder())).thenReturn(1);

            PayOrderResponse resp = orderService.payOrder(1L, 1001L);

            assertEquals("PAID", resp.getStatus());
            assertNotNull(resp.getPaidAt());

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderMapper).updateById((Order) captor.capture());
            assertEquals("PAID", captor.getValue().getStatus());
            assertNotNull(captor.getValue().getPaidAt());
        }

        @Test
        @DisplayName("订单状态不为 PENDING_PAYMENT 时抛出 BusinessException")
        void wrongStatus_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "PAID"));

            assertThrows(BusinessException.class, () -> orderService.payOrder(1L, 1001L));
            verify(orderMapper, never()).updateById(anyOrder());
        }
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrderTests {

        @Test
        @DisplayName("取消 PENDING_PAYMENT 状态订单，验证库存恢复、refunded 为 false")
        void cancelPendingPayment_refundedFalse() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "PENDING_PAYMENT"));
            when(orderItemMapper.selectList(any())).thenReturn(List.of(
                    OrderItem.builder().orderId(1001L).productId(101L).quantity(2).build()));
            when(productMapper.selectById(101L)).thenReturn(
                    product(101L, "商品A", new BigDecimal("10.00"), 48, 100L));
            when(productMapper.updateById(anyProduct())).thenReturn(1);
            when(orderMapper.updateById(anyOrder())).thenReturn(1);

            CancelOrderResponse resp = orderService.cancelOrder(1L, 1001L);

            assertEquals("CANCELLED", resp.getStatus());
            assertFalse(resp.getRefunded());
            // 库存恢复：48 + 2 = 50
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productMapper).updateById((Product) captor.capture());
            assertEquals(50, captor.getValue().getStock());
        }

        @Test
        @DisplayName("取消 PAID 状态订单，验证库存被恢复、refunded 为 true")
        void cancelPaid_inventoryRestoredAndRefundedTrue() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "PAID"));
            when(orderItemMapper.selectList(any())).thenReturn(List.of(
                    OrderItem.builder().orderId(1001L).productId(101L).quantity(3).build()));
            when(productMapper.selectById(101L)).thenReturn(
                    product(101L, "商品A", new BigDecimal("10.00"), 7, 100L)); // 当前库存 7
            when(productMapper.updateById(anyProduct())).thenReturn(1);
            when(orderMapper.updateById(anyOrder())).thenReturn(1);

            CancelOrderResponse resp = orderService.cancelOrder(1L, 1001L);

            assertEquals("CANCELLED", resp.getStatus());
            assertTrue(resp.getRefunded());
            // 7 + 3 = 10
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productMapper).updateById((Product) captor.capture());
            assertEquals(10, captor.getValue().getStock());
        }

        @Test
        @DisplayName("使用了优惠券的订单取消时验证优惠券状态恢复为 UNUSED")
        void withCoupon_couponRestored() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L))
                    .thenReturn(orderWithCoupon(1001L, 1L, 100L, "PAID", 5L));
            when(orderItemMapper.selectList(any())).thenReturn(List.of(
                    OrderItem.builder().orderId(1001L).productId(101L).quantity(1).build()));
            when(productMapper.selectById(101L)).thenReturn(
                    product(101L, "商品A", new BigDecimal("10.00"), 9, 100L));
            when(productMapper.updateById(anyProduct())).thenReturn(1);
            when(couponMapper.update(isNull(), any())).thenReturn(1);
            when(orderMapper.updateById(anyOrder())).thenReturn(1);

            orderService.cancelOrder(1L, 1001L);

            // restoreCoupon 通过 couponMapper.update(null, wrapper) 清除使用记录
            verify(couponMapper).update(isNull(), any());
        }

        @Test
        @DisplayName("订单状态为 SHIPPED 时抛出 BusinessException")
        void shippedOrder_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "SHIPPED"));

            assertThrows(BusinessException.class, () -> orderService.cancelOrder(1L, 1001L));
            verify(orderMapper, never()).updateById(anyOrder());
        }
    }

    // ── cancelExpiredUnpaidOrder（定时任务，系统级无鉴权）──────────────────────

    @Nested
    @DisplayName("cancelExpiredUnpaidOrder")
    class CancelExpiredUnpaidOrderTests {

        @Test
        @DisplayName("取消过期未支付订单：恢复库存、置为 CANCELLED、记录取消时间")
        void cancelsExpiredOrder_restoresStock() {
            // 系统级方法不读 SecurityContext，无需 mockAuthUser
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "PENDING_PAYMENT"));
            when(orderItemMapper.selectList(any())).thenReturn(List.of(
                    OrderItem.builder().orderId(1001L).productId(101L).quantity(2).build()));
            when(productMapper.selectById(101L)).thenReturn(
                    product(101L, "商品A", new BigDecimal("10.00"), 48, 100L));
            when(productMapper.updateById(anyProduct())).thenReturn(1);
            when(orderMapper.updateById(anyOrder())).thenReturn(1);

            orderService.cancelExpiredUnpaidOrder(Order.builder().id(1001L).build());

            // 库存恢复：48 + 2 = 50
            ArgumentCaptor<Product> pc = ArgumentCaptor.forClass(Product.class);
            verify(productMapper).updateById((Product) pc.capture());
            assertEquals(50, pc.getValue().getStock());
            // 订单状态置为 CANCELLED 且记录取消时间
            ArgumentCaptor<Order> oc = ArgumentCaptor.forClass(Order.class);
            verify(orderMapper).updateById((Order) oc.capture());
            assertEquals("CANCELLED", oc.getValue().getStatus());
            assertNotNull(oc.getValue().getCancelledAt());
        }

        @Test
        @DisplayName("使用了优惠券的过期订单取消时恢复优惠券为 UNUSED")
        void withCoupon_couponRestored() {
            when(orderMapper.selectById(1001L))
                    .thenReturn(orderWithCoupon(1001L, 1L, 100L, "PENDING_PAYMENT", 5L));
            when(orderItemMapper.selectList(any())).thenReturn(List.of(
                    OrderItem.builder().orderId(1001L).productId(101L).quantity(1).build()));
            when(productMapper.selectById(101L)).thenReturn(
                    product(101L, "商品A", new BigDecimal("10.00"), 9, 100L));
            when(productMapper.updateById(anyProduct())).thenReturn(1);
            when(couponMapper.update(isNull(), any())).thenReturn(1);
            when(orderMapper.updateById(anyOrder())).thenReturn(1);

            orderService.cancelExpiredUnpaidOrder(Order.builder().id(1001L).build());

            verify(couponMapper).update(isNull(), any());
        }

        @Test
        @DisplayName("订单已非 PENDING_PAYMENT（如已支付）时跳过，不做任何写操作")
        void alreadyPaid_skipped() {
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "PAID"));

            orderService.cancelExpiredUnpaidOrder(Order.builder().id(1001L).build());

            verify(orderMapper, never()).updateById(anyOrder());
            verify(productMapper, never()).updateById(anyProduct());
            verify(orderItemMapper, never()).selectList(any());
        }
    }

    // ── confirmReceived ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("confirmReceived")
    class ConfirmReceivedTests {

        @Test
        @DisplayName("正常确认收货，验证状态变更为 COMPLETED 且 completedAt 被记录")
        void success_statusCompleted() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "SHIPPED"));
            when(orderMapper.updateById(anyOrder())).thenReturn(1);

            ConfirmOrderResponse resp = orderService.confirmReceived(1L, 1001L);

            assertEquals("COMPLETED", resp.getStatus());

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderMapper).updateById((Order) captor.capture());
            assertEquals("COMPLETED", captor.getValue().getStatus());
            assertNotNull(captor.getValue().getCompletedAt());
        }

        @Test
        @DisplayName("订单状态不为 SHIPPED 时抛出 BusinessException")
        void wrongStatus_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "PAID"));

            assertThrows(BusinessException.class, () -> orderService.confirmReceived(1L, 1001L));
            verify(orderMapper, never()).updateById(anyOrder());
        }
    }

    // ── refundOrder ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refundOrder")
    class RefundOrderTests {

        @Test
        @DisplayName("正常退款，验证库存恢复、优惠券状态恢复、refundedAt 被记录")
        void success_inventoryAndCouponRestored() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L))
                    .thenReturn(orderWithCoupon(1001L, 1L, 100L, "COMPLETED", 5L));
            when(orderItemMapper.selectList(any())).thenReturn(List.of(
                    OrderItem.builder().orderId(1001L).productId(101L).quantity(2).build()));
            when(productMapper.selectById(101L)).thenReturn(
                    product(101L, "商品A", new BigDecimal("10.00"), 18, 100L));
            when(productMapper.updateById(anyProduct())).thenReturn(1);
            when(couponMapper.update(isNull(), any())).thenReturn(1);
            when(orderMapper.updateById(anyOrder())).thenReturn(1);

            RefundResponse resp = orderService.refundOrder(1L, 1001L, refundReq());

            assertEquals("CANCELLED", resp.getStatus());
            assertNotNull(resp.getRefundedAt());

            // 库存验证：18 + 2 = 20
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productMapper).updateById((Product) captor.capture());
            assertEquals(20, captor.getValue().getStock());

            // 优惠券恢复验证
            verify(couponMapper).update(isNull(), any());
        }

        @Test
        @DisplayName("订单状态不为 COMPLETED 时抛出 BusinessException")
        void wrongStatus_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "SHIPPED"));

            assertThrows(BusinessException.class,
                    () -> orderService.refundOrder(1L, 1001L, refundReq()));
            verify(orderMapper, never()).updateById(anyOrder());
        }
    }

    // ── shipOrder ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("shipOrder")
    class ShipOrderTests {

        @Test
        @DisplayName("正常发货，验证状态变更为 SHIPPED 且物流信息被保存")
        void success_shippedWithLogistics() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "PAID"));
            when(orderMapper.updateById(anyOrder())).thenReturn(1);

            ShipOrderResponse resp = orderService.shipOrder(100L, 1001L, shipReq("SF123", "顺丰"));

            assertEquals("SHIPPED", resp.getStatus());
            assertNotNull(resp.getShippedAt());

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderMapper).updateById((Order) captor.capture());
            assertEquals("SHIPPED", captor.getValue().getStatus());
            assertEquals("SF123", captor.getValue().getTrackingNumber());
            assertEquals("顺丰",   captor.getValue().getCarrier());
            assertNotNull(captor.getValue().getShippedAt());
        }

        @Test
        @DisplayName("订单状态不为 PAID 时抛出 BusinessException")
        void wrongStatus_throws() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 100L, "SHIPPED"));

            assertThrows(BusinessException.class,
                    () -> orderService.shipOrder(100L, 1001L, shipReq("SF123", "顺丰")));
            verify(orderMapper, never()).updateById(anyOrder());
        }

        @Test
        @DisplayName("Staff 操作其他店铺订单时抛出 BusinessException（code 403）")
        void wrongStore_throws403() {
            // Staff 的 storeId=100，但订单属于 storeId=200
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);
            when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, 200L, "PAID"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> orderService.shipOrder(100L, 1001L, shipReq("SF123", "顺丰")));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verify(orderMapper, never()).updateById(anyOrder());
        }
    }
}
