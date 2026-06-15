package com.twentyzhang.bluewhale.dto.payment;

import lombok.*;
import java.math.BigDecimal;

/** 支付回调请求体（webhook）。真支付宝调 /notify 时也是这个形态。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotifyRequest {
    private String tradeNo;
    private String status;      // SUCCESS / FAILED
    private BigDecimal amount;
    private String sign;        // HMAC 签名
}
