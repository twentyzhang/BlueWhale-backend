package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.AddCartItemRequest;
import com.twentyzhang.bluewhale.dto.CartResponse;
import com.twentyzhang.bluewhale.dto.UpdateCartItemRequest;
import com.twentyzhang.bluewhale.entity.Cart;
import com.twentyzhang.bluewhale.mapper.CartMapper;
import com.twentyzhang.bluewhale.service.CartService;
import org.springframework.stereotype.Service;

@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements CartService {

    @Override
    public CartResponse getCart(Long userId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void addItem(Long userId, AddCartItemRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void updateItemQuantity(Long userId, Long itemId, UpdateCartItemRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void removeItem(Long userId, Long itemId) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
