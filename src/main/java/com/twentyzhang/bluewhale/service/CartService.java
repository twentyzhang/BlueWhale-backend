package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.dto.AddCartItemRequest;
import com.twentyzhang.bluewhale.dto.CartResponse;
import com.twentyzhang.bluewhale.dto.UpdateCartItemRequest;
import com.twentyzhang.bluewhale.entity.Cart;

public interface CartService extends IService<Cart> {

    /**
     * 获取当前用户的购物车及其所有条目（含商品名称、价格、库存等信息）。
     * 若该用户尚无购物车，则自动创建。
     * 对应 GET /api/cart
     */
    CartResponse getCart(Long userId);

    /**
     * 将指定商品加入购物车。若该商品已在购物车中，则数量累加；否则新增条目。
     * 对应 POST /api/cart/items
     */
    void addItem(Long userId, AddCartItemRequest request);

    /**
     * 修改购物车中指定条目的商品数量。校验条目属于当前用户购物车。
     * 对应 PUT /api/cart/items/{itemId}
     */
    void updateItemQuantity(Long userId, Long itemId, UpdateCartItemRequest request);

    /**
     * 删除购物车中的指定条目。校验条目属于当前用户购物车。
     * 对应 DELETE /api/cart/items/{itemId}
     */
    void removeItem(Long userId, Long itemId);
}
