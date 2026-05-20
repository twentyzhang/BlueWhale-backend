package com.twentyzhang.bluewhale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderResponse {

    private String status;
    /** 已支付订单取消时为 true，未支付时为 false */
    private Boolean refunded;
}
