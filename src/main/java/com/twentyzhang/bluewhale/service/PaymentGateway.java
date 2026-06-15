package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.dto.payment.PaymentNotifyRequest;

import java.math.BigDecimal;

/** 支付提供方抽象（可插拔）：现 MockPaymentGateway，将来 AlipayPaymentGateway 替换。 */
public interface PaymentGateway {

    /** 创建支付，返回支付链接（mock 返回 /api/mock-pay/{tradeNo}）。 */
    String createPayment(Long orderId, String tradeNo, BigDecimal amount);

    /** 验证回调签名（mock=HMAC，未来 Alipay=RSA）。 */
    boolean verifyCallback(PaymentNotifyRequest callback);
}
