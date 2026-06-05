package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 下单前的优惠券试算请求：传待结算购物车条目与候选券，返回最优优惠。
 * 不创建订单、不核销券，仅计算。
 */
@Data
public class CouponPreviewRequest {

    @NotEmpty(message = "购物车条目不能为空")
    private List<Long> cartItemIds;

    /** 候选优惠券 ID 列表，可为 null/空（表示不使用券，返回原价）。 */
    private List<Long> couponIds;
}
