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

    /**
     * 使用的优惠券 ID 列表，不使用时传 null 或空列表。
     * 同类型（折扣/满减/直减）至多一张，不同类型可叠加；后台自动按最优顺序计价。
     */
    private List<Long> couponIds;
}
