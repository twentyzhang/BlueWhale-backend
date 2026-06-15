package com.twentyzhang.bluewhale.service.impl;

import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.payment.InitiatePaymentResponse;
import com.twentyzhang.bluewhale.dto.payment.PaymentNotifyRequest;
import com.twentyzhang.bluewhale.dto.payment.PaymentStatusResponse;
import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.entity.Payment;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.mapper.PaymentMapper;
import com.twentyzhang.bluewhale.service.PaymentGateway;
import com.twentyzhang.bluewhale.service.PaymentService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.PaymentSignUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_SUCCESS = "SUCCESS";
    static final String STATUS_FAILED  = "FAILED";
    static final String CHANNEL_MOCK   = "MOCK";

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final PaymentGateway paymentGateway;

    @Qualifier("paymentCallbackScheduler")
    private final TaskScheduler callbackScheduler;

    @Value("${payment.mock.secret}")
    private String secret;

    @Value("${payment.mock.callback-delay-ms}")
    private long callbackDelayMs;

    // ── 发起支付（REST，Customer 本人） ──────────────────────────────────────────
    @Override
    public InitiatePaymentResponse initiatePayment(Long userId, Long orderId) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);
        Order order = orderMapper.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "订单不存在");
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException("订单无法支付，当前状态：" + order.getStatus() + "（需为 PENDING_PAYMENT）");
        }
        String tradeNo = UUID.randomUUID().toString().replace("-", "");
        Payment payment = Payment.builder()
                .orderId(orderId)
                .tradeNo(tradeNo)
                .amount(order.getPayableAmount())
                .status(STATUS_PENDING)
                .channel(CHANNEL_MOCK)
                .build();
        paymentMapper.insert(payment);

        String payUrl = paymentGateway.createPayment(orderId, tradeNo, order.getPayableAmount());
        return InitiatePaymentResponse.builder()
                .tradeNo(tradeNo).payUrl(payUrl).amount(order.getPayableAmount()).build();
    }

    // ── 回调处理（webhook / 异步，无 SecurityContext） ──────────────────────────
    @Override
    @Transactional
    public void handleNotify(PaymentNotifyRequest notify) {
        if (!paymentGateway.verifyCallback(notify)) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "支付回调验签失败");
        }
        Payment payment = paymentMapper.selectByTradeNo(notify.getTradeNo());
        if (payment == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "未知交易号");
        }
        if (!STATUS_PENDING.equals(payment.getStatus())) {
            log.info("支付回调幂等：流水 {} 已是终态 {}，忽略", notify.getTradeNo(), payment.getStatus());
            return;   // 幂等：已处理过
        }
        if (payment.getAmount().compareTo(notify.getAmount()) != 0) {
            throw new BusinessException(Result.CODE_BAD_REQUEST, "支付金额不一致");
        }

        boolean success = STATUS_SUCCESS.equals(notify.getStatus());
        LocalDateTime now = LocalDateTime.now();
        int advanced = paymentMapper.advanceStatus(
                notify.getTradeNo(), success ? STATUS_SUCCESS : STATUS_FAILED, success ? now : null);
        if (advanced == 0) {
            log.info("支付回调幂等：流水 {} 已被并发回调处理", notify.getTradeNo());
            return;   // 并发已处理
        }
        if (success) {
            int paid = orderMapper.markPaid(payment.getOrderId(), now);
            if (paid == 0) {
                log.warn("支付成功但订单 {} 已非待支付（已取消/已支付），仅记流水", payment.getOrderId());
            }
        }
    }

    // ── 模拟收银台触发：调度延迟异步回调 ─────────────────────────────────────────
    @Override
    public void triggerMockCallback(String tradeNo, boolean success) {
        Payment payment = paymentMapper.selectByTradeNo(tradeNo);
        if (payment == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "交易不存在");
        }
        String status = success ? STATUS_SUCCESS : STATUS_FAILED;
        PaymentNotifyRequest notify = PaymentNotifyRequest.builder()
                .tradeNo(tradeNo)
                .status(status)
                .amount(payment.getAmount())
                .sign(PaymentSignUtil.sign(tradeNo, status, payment.getAmount(), secret))
                .build();
        // 延迟异步回调，模拟支付宝回调晚到（前端需轮询查单）
        callbackScheduler.schedule(() -> {
            try {
                handleNotify(notify);
            } catch (Exception e) {
                log.error("模拟支付回调处理失败，tradeNo={}", tradeNo, e);
            }
        }, Instant.now().plusMillis(callbackDelayMs));
    }

    // ── 查单（REST，Customer 本人） ──────────────────────────────────────────────
    @Override
    public PaymentStatusResponse queryPayment(Long userId, String tradeNo) {
        Payment payment = paymentMapper.selectByTradeNo(tradeNo);
        if (payment == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "交易不存在");
        }
        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权查看该支付");
        }
        return PaymentStatusResponse.builder()
                .tradeNo(payment.getTradeNo())
                .status(payment.getStatus())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
