package com.twentyzhang.bluewhale.dto.payment;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentResponse {
    /** 交易号（前端轮询查单用） */
    private String tradeNo;
    /** 模拟支付链接（/api/mock-pay/{tradeNo}） */
    private String payUrl;
    /** 应付金额 */
    private BigDecimal amount;
}
