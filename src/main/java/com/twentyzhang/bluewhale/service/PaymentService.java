package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.dto.payment.InitiatePaymentResponse;
import com.twentyzhang.bluewhale.dto.payment.PaymentNotifyRequest;
import com.twentyzhang.bluewhale.dto.payment.PaymentStatusResponse;

public interface PaymentService {

    /** 发起支付（Customer 本人）：建 PENDING 流水，返回 tradeNo + payUrl。 */
    InitiatePaymentResponse initiatePayment(Long userId, Long orderId);

    /** 处理支付回调（webhook / 异步线程，不读 SecurityContext）：验签 + 幂等 + 推进状态。 */
    void handleNotify(PaymentNotifyRequest notify);

    /** 模拟收银台触发：校验流水后调度延迟异步回调（success=SUCCESS，否则 FAILED）。 */
    void triggerMockCallback(String tradeNo, boolean success);

    /** 查单（Customer 本人订单）：供前端轮询支付状态。 */
    PaymentStatusResponse queryPayment(Long userId, String tradeNo);
}
