package com.twentyzhang.bluewhale.service.impl;

import com.twentyzhang.bluewhale.dto.payment.PaymentNotifyRequest;
import com.twentyzhang.bluewhale.service.PaymentGateway;
import com.twentyzhang.bluewhale.util.PaymentSignUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Value("${payment.mock.secret}")
    private String secret;

    @Override
    public String createPayment(Long orderId, String tradeNo, BigDecimal amount) {
        // mock：支付链接即模拟收银台端点；真 Alipay 这里会调其创建支付 API 拿跳转/二维码链接
        return "/api/mock-pay/" + tradeNo;
    }

    @Override
    public boolean verifyCallback(PaymentNotifyRequest c) {
        return PaymentSignUtil.verify(c.getTradeNo(), c.getStatus(), c.getAmount(), secret, c.getSign());
    }
}
