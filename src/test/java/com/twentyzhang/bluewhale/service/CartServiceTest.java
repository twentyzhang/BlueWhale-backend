package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.AddCartItemRequest;
import com.twentyzhang.bluewhale.dto.CartItemResponse;
import com.twentyzhang.bluewhale.dto.CartResponse;
import com.twentyzhang.bluewhale.dto.UpdateCartItemRequest;
import com.twentyzhang.bluewhale.entity.Cart;
import com.twentyzhang.bluewhale.entity.CartItem;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.CartItemMapper;
import com.twentyzhang.bluewhale.mapper.CartMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.service.impl.CartServiceImpl;
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

@DisplayName("CartService")
class CartServiceTest extends BaseServiceTest {

    @Mock private CartMapper     cartMapper;
    @Mock private CartItemMapper cartItemMapper;
    @Mock private ProductMapper  productMapper;

    @InjectMocks
    private CartServiceImpl cartService;

    // disambiguation helpers（BaseMapper 3.5.9 insert/updateById 重载，见 debug记录.md 第 1 条）
    private static Cart     anyCart()     { return ArgumentMatchers.any(Cart.class); }
    private static CartItem anyCartItem() { return ArgumentMatchers.any(CartItem.class); }

    @BeforeEach
    void setUp() {
        // baseMapper 是父类 ServiceImpl 的泛型字段，需显式注入（见 debug记录.md 第 3 条）
        ReflectionTestUtils.setField(cartService, "baseMapper", cartMapper);
    }

    // ── 构建辅助 ──────────────────────────────────────────────────────────────

    private static Cart cart(Long id, Long userId) {
        return Cart.builder().id(id).userId(userId).build();
    }

    private static CartItem item(Long id, Long cartId, Long productId, int qty) {
        return CartItem.builder().id(id).cartId(cartId).productId(productId).quantity(qty).build();
    }

    private static Product product(Long id, BigDecimal price, int stock) {
        return Product.builder().id(id).name("商品" + id).price(price).stock(stock).build();
    }

    private static AddCartItemRequest addReq(Long productId, int qty) {
        AddCartItemRequest r = new AddCartItemRequest();
        r.setProductId(productId);
        r.setQuantity(qty);
        return r;
    }

    private static UpdateCartItemRequest updateReq(int qty) {
        UpdateCartItemRequest r = new UpdateCartItemRequest();
        r.setQuantity(qty);
        return r;
    }

    // ── getCart ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCart")
    class GetCartTests {

        @Test
        @DisplayName("正常返回购物车，subtotal 和 total 计算正确")
        void success_correctSubtotalAndTotal() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));
            when(cartItemMapper.selectList(any())).thenReturn(List.of(
                    item(1L, 10L, 101L, 2),   // 2 × 10.00 = 20.00
                    item(2L, 10L, 102L, 3)    // 3 × 15.00 = 45.00
            ));
            when(productMapper.selectBatchIds(any())).thenReturn(List.of(
                    product(101L, new BigDecimal("10.00"), 50),
                    product(102L, new BigDecimal("15.00"), 30)
            ));

            CartResponse resp = cartService.getCart(1L);

            assertEquals(2, resp.getItems().size());
            CartItemResponse a = resp.getItems().stream()
                    .filter(i -> i.getProductId().equals(101L)).findFirst().orElseThrow();
            CartItemResponse b = resp.getItems().stream()
                    .filter(i -> i.getProductId().equals(102L)).findFirst().orElseThrow();
            assertEquals(new BigDecimal("20.00"), a.getSubtotal());
            assertEquals(new BigDecimal("45.00"), b.getSubtotal());
            assertEquals(new BigDecimal("65.00"), resp.getTotal());
        }

        @Test
        @DisplayName("购物车为空时返回空列表且 total 为 0")
        void emptyCart_returnsZeroTotal() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));
            when(cartItemMapper.selectList(any())).thenReturn(List.of());

            CartResponse resp = cartService.getCart(1L);

            assertTrue(resp.getItems().isEmpty());
            assertEquals(BigDecimal.ZERO, resp.getTotal());
        }

        @Test
        @DisplayName("商品已删除时对应条目 stock 返回 0，subtotal 为 0")
        void deletedProduct_stockAndSubtotalAreZero() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));
            when(cartItemMapper.selectList(any())).thenReturn(List.of(item(1L, 10L, 999L, 2)));
            // selectBatchIds 不返回逻辑删除的商品
            when(productMapper.selectBatchIds(any())).thenReturn(List.of());

            CartResponse resp = cartService.getCart(1L);

            assertEquals(1, resp.getItems().size());
            CartItemResponse r = resp.getItems().get(0);
            assertEquals(0, r.getStock());
            assertEquals(BigDecimal.ZERO, r.getSubtotal());
        }
    }

    // ── addItem ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("addItem")
    class AddItemTests {

        @Test
        @DisplayName("正常加入购物车（新条目）")
        void success_newItem() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(productMapper.selectById(101L)).thenReturn(product(101L, new BigDecimal("10.00"), 50));
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));
            when(cartItemMapper.selectOne(any())).thenReturn(null);
            when(cartItemMapper.insert(anyCartItem())).thenReturn(1);

            cartService.addItem(1L, addReq(101L, 3));

            ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
            verify(cartItemMapper).insert((CartItem) captor.capture());
            assertEquals(101L, captor.getValue().getProductId());
            assertEquals(3,    captor.getValue().getQuantity());
            assertEquals(10L,  captor.getValue().getCartId());
            verify(cartItemMapper, never()).updateById(anyCartItem());
        }

        @Test
        @DisplayName("商品已在购物车中时数量累加")
        void success_quantityCumulated() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(productMapper.selectById(101L)).thenReturn(product(101L, new BigDecimal("10.00"), 50));
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));
            when(cartItemMapper.selectOne(any())).thenReturn(item(1L, 10L, 101L, 2));
            when(cartItemMapper.updateById(anyCartItem())).thenReturn(1);

            cartService.addItem(1L, addReq(101L, 3));

            ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
            verify(cartItemMapper).updateById((CartItem) captor.capture());
            assertEquals(5, captor.getValue().getQuantity()); // 2 + 3 = 5
            verify(cartItemMapper, never()).insert(anyCartItem());
        }

        @Test
        @DisplayName("商品不存在时抛出 code=404 的 BusinessException")
        void productNotFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(productMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.addItem(1L, addReq(999L, 1)));

            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verifyNoInteractions(cartMapper, cartItemMapper);
        }

        @Test
        @DisplayName("累加后数量超过库存时抛出 BusinessException")
        void quantityExceedsStock_throwsBadRequest() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(productMapper.selectById(101L)).thenReturn(product(101L, new BigDecimal("10.00"), 10));
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));
            when(cartItemMapper.selectOne(any())).thenReturn(item(1L, 10L, 101L, 8));

            // 8 + 5 = 13 > 10（库存）
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.addItem(1L, addReq(101L, 5)));

            assertEquals(Result.CODE_BAD_REQUEST, ex.getCode());
            verify(cartItemMapper, never()).updateById(anyCartItem());
            verify(cartItemMapper, never()).insert(anyCartItem());
        }

        @Test
        @DisplayName("非 Customer 调用时抛出 code=403 的 BusinessException")
        void notCustomer_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.addItem(1L, addReq(101L, 1)));

            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verifyNoInteractions(productMapper, cartMapper, cartItemMapper);
        }
    }

    // ── updateItem ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateItem")
    class UpdateItemTests {

        @Test
        @DisplayName("正常修改数量")
        void success_updatesQuantity() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectById(1L)).thenReturn(item(1L, 10L, 101L, 2));
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));
            when(productMapper.selectById(101L)).thenReturn(product(101L, new BigDecimal("10.00"), 20));
            when(cartItemMapper.updateById(anyCartItem())).thenReturn(1);

            cartService.updateItemQuantity(1L, 1L, updateReq(5));

            ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
            verify(cartItemMapper).updateById((CartItem) captor.capture());
            assertEquals(5, captor.getValue().getQuantity());
        }

        @Test
        @DisplayName("数量改为 0 时条目被物理删除")
        void quantityZero_itemDeleted() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectById(1L)).thenReturn(item(1L, 10L, 101L, 3));
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));

            cartService.updateItemQuantity(1L, 1L, updateReq(0));

            verify(cartItemMapper).deleteById((Serializable) 1L);
            verify(cartItemMapper, never()).updateById(anyCartItem());
            // quantity=0 提前返回，不应查商品
            verifyNoInteractions(productMapper);
        }

        @Test
        @DisplayName("修改后数量超过库存时抛出 BusinessException")
        void quantityExceedsStock_throwsBadRequest() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectById(1L)).thenReturn(item(1L, 10L, 101L, 2));
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));
            when(productMapper.selectById(101L)).thenReturn(product(101L, new BigDecimal("10.00"), 10));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.updateItemQuantity(1L, 1L, updateReq(15))); // 15 > 10

            assertEquals(Result.CODE_BAD_REQUEST, ex.getCode());
            verify(cartItemMapper, never()).updateById(anyCartItem());
        }

        @Test
        @DisplayName("条目不存在时抛出 code=404 的 BusinessException")
        void itemNotFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.updateItemQuantity(1L, 999L, updateReq(2)));

            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
        }

        @Test
        @DisplayName("操作其他用户的条目时抛出 code=403 的 BusinessException")
        void otherUserItem_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            // item 属于 cart 99，当前用户的 cart 是 10
            when(cartItemMapper.selectById(1L)).thenReturn(item(1L, 99L, 101L, 2));
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.updateItemQuantity(1L, 1L, updateReq(2)));

            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
        }
    }

    // ── removeItem ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeItem")
    class RemoveItemTests {

        @Test
        @DisplayName("正常删除条目")
        void success_itemDeleted() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectById(1L)).thenReturn(item(1L, 10L, 101L, 2));
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));

            cartService.removeItem(1L, 1L);

            verify(cartItemMapper).deleteById((Serializable) 1L);
        }

        @Test
        @DisplayName("条目不存在时抛出 code=404 的 BusinessException")
        void itemNotFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.removeItem(1L, 999L));

            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
        }

        @Test
        @DisplayName("删除其他用户的条目时抛出 code=403 的 BusinessException")
        void otherUserItem_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(cartItemMapper.selectById(1L)).thenReturn(item(1L, 99L, 101L, 2));
            when(cartMapper.selectOne(any(), anyBoolean())).thenReturn(cart(10L, 1L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> cartService.removeItem(1L, 1L));

            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
        }
    }
}
