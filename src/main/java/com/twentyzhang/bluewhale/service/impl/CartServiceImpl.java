package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import com.twentyzhang.bluewhale.service.CartService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;

    // ── 1. getCart ────────────────────────────────────────────────────────────
    @Override
    public CartResponse getCart(Long userId) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        Cart cart = getOrCreateCart(userId);

        List<CartItem> items = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>().eq(CartItem::getCartId, cart.getId()));

        if (items.isEmpty()) {
            return CartResponse.builder().items(Collections.emptyList()).total(BigDecimal.ZERO).build();
        }

        // 批量查商品（逻辑删除的商品不会出现在结果中）
        List<Long> productIds = items.stream().map(CartItem::getProductId).collect(Collectors.toList());
        Map<Long, Product> productMap = new HashMap<>();
        productMapper.selectBatchIds(productIds).forEach(p -> productMap.put(p.getId(), p));

        List<CartItemResponse> responseItems = items.stream().map(item -> {
            Product product = productMap.get(item.getProductId());
            if (product == null) {
                // 商品已被删除或下架：条目保留，stock 标记为 0，subtotal 为 0
                return CartItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName("（商品已下架）")
                        .imageUrl(null)
                        .price(BigDecimal.ZERO)
                        .quantity(item.getQuantity())
                        .stock(0)
                        .subtotal(BigDecimal.ZERO)
                        .build();
            }
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return CartItemResponse.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .productName(product.getName())
                    .imageUrl(product.getImageUrl())
                    .price(product.getPrice())
                    .quantity(item.getQuantity())
                    .stock(product.getStock())
                    .subtotal(subtotal)
                    .build();
        }).collect(Collectors.toList());

        BigDecimal total = responseItems.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder().items(responseItems).total(total).build();
    }

    // ── 2. addItem ────────────────────────────────────────────────────────────
    @Override
    public void addItem(Long userId, AddCartItemRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        Product product = productMapper.selectById(request.getProductId());
        if (product == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }

        Cart cart = getOrCreateCart(userId);

        CartItem existing = cartItemMapper.selectOne(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getCartId, cart.getId())
                        .eq(CartItem::getProductId, request.getProductId()));

        if (existing != null) {
            int newQuantity = existing.getQuantity() + request.getQuantity();
            if (newQuantity > product.getStock()) {
                throw new BusinessException("数量超出库存，当前库存 " + product.getStock());
            }
            existing.setQuantity(newQuantity);
            cartItemMapper.updateById(existing);
        } else {
            if (request.getQuantity() > product.getStock()) {
                throw new BusinessException("数量超出库存，当前库存 " + product.getStock());
            }
            cartItemMapper.insert(CartItem.builder()
                    .cartId(cart.getId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .build());
        }
    }

    // ── 3. updateItemQuantity ─────────────────────────────────────────────────
    @Override
    public void updateItemQuantity(Long userId, Long itemId, UpdateCartItemRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        CartItem item = getItemForUser(userId, itemId);

        if (request.getQuantity() == 0) {
            cartItemMapper.deleteById(itemId);
            return;
        }

        // 商品被删除时 stock 视为 0，禁止更新数量
        Product product = productMapper.selectById(item.getProductId());
        int maxStock = product != null ? product.getStock() : 0;
        if (request.getQuantity() > maxStock) {
            throw new BusinessException("数量超出库存，当前库存 " + maxStock);
        }

        item.setQuantity(request.getQuantity());
        cartItemMapper.updateById(item);
    }

    // ── 4. removeItem ─────────────────────────────────────────────────────────
    @Override
    public void removeItem(Long userId, Long itemId) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);
        getItemForUser(userId, itemId);
        cartItemMapper.deleteById(itemId);
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

    /** 查询用户购物车，不存在则自动创建。 */
    private Cart getOrCreateCart(Long userId) {
        Cart cart = getOne(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));
        if (cart == null) {
            cart = Cart.builder().userId(userId).build();
            save(cart);
        }
        return cart;
    }

    /**
     * 查购物车条目并校验归属：
     * - 条目不存在 → 404
     * - 条目不属于当前用户的购物车 → 403
     */
    private CartItem getItemForUser(Long userId, Long itemId) {
        CartItem item = cartItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "购物车条目不存在");
        }
        Cart cart = getOne(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));
        if (cart == null || !cart.getId().equals(item.getCartId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权操作该购物车条目");
        }
        return item;
    }
}
