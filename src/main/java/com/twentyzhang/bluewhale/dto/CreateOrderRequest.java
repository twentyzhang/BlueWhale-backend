package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotEmpty(message = "购物车条目不能为空")
    private List<Long> cartItemIds;

    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    /** 不使用优惠券时传 null */
    private Long couponId;
}
