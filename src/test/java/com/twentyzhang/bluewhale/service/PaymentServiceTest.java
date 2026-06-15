package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.dto.payment.InitiatePaymentResponse;
import com.twentyzhang.bluewhale.dto.payment.PaymentNotifyRequest;
import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.entity.Payment;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.mapper.PaymentMapper;
import com.twentyzhang.bluewhale.service.impl.MockPaymentGateway;
import com.twentyzhang.bluewhale.service.impl.PaymentServiceImpl;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.PaymentSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PaymentService")
class PaymentServiceTest extends BaseServiceTest {

    private static final String SECRET = "test-secret";

    @Mock private PaymentMapper paymentMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private TaskScheduler callbackScheduler;

    private final PaymentGateway gateway = new MockPaymentGateway();
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gateway, "secret", SECRET);
        service = new PaymentServiceImpl(paymentMapper, orderMapper, gateway, callbackScheduler);
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "callbackDelayMs", 0L);
    }

    private static Order order(Long id, Long userId, String status) {
        return Order.builder().id(id).userId(userId).status(status)
                .payableAmount(new BigDecimal("9.90")).build();
    }

    private static Payment payment(String tradeNo, Long orderId, String status) {
        return Payment.builder().tradeNo(tradeNo).orderId(orderId)
                .amount(new BigDecimal("9.90")).status(status).channel("MOCK").build();
    }

    private PaymentNotifyRequest signedNotify(String tradeNo, String status, BigDecimal amount) {
        return PaymentNotifyRequest.builder().tradeNo(tradeNo).status(status).amount(amount)
                .sign(PaymentSignUtil.sign(tradeNo, status, amount, SECRET)).build();
    }

    // ── initiatePayment ──
    @Test
    @DisplayName("发起支付：建 PENDING 流水并返回 tradeNo + payUrl")
    void initiate_createsPendingPayment() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, "PENDING_PAYMENT"));

        InitiatePaymentResponse resp = service.initiatePayment(1L, 1001L);

        assertNotNull(resp.getTradeNo());
        assertTrue(resp.getPayUrl().startsWith("/api/mock-pay/"));
        assertEquals(new BigDecimal("9.90"), resp.getAmount());
        verify(paymentMapper).insert(any(Payment.class));
    }

    @Test
    @DisplayName("发起支付：订单非待支付 → 抛异常，不建流水")
    void initiate_wrongStatus_throws() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, "PAID"));

        assertThrows(BusinessException.class, () -> service.initiatePayment(1L, 1001L));
        verify(paymentMapper, never()).insert(any(Payment.class));
    }

    @Test
    @DisplayName("发起支付：非本人订单 → 404")
    void initiate_notOwner_throws404() {
        mockAuthUser(2L, AuthUtil.ROLE_CUSTOMER, null);
        when(orderMapper.selectById(1001L)).thenReturn(order(1001L, 1L, "PENDING_PAYMENT"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.initiatePayment(2L, 1001L));
        assertEquals(404, ex.getCode());
    }

    // ── handleNotify ──
    @Test
    @DisplayName("回调成功：流水推进 + 订单置 PAID")
    void notify_success_advancesAndMarksPaid() {
        when(paymentMapper.selectByTradeNo("T1")).thenReturn(payment("T1", 1001L, "PENDING"));
        when(paymentMapper.advanceStatus(eq("T1"), eq("SUCCESS"), any())).thenReturn(1);

        service.handleNotify(signedNotify("T1", "SUCCESS", new BigDecimal("9.90")));

        verify(paymentMapper).advanceStatus(eq("T1"), eq("SUCCESS"), any());
        verify(orderMapper).markPaid(eq(1001L), any());
    }

    @Test
    @DisplayName("回调验签失败 → 抛异常，不改任何状态")
    void notify_badSign_throws() {
        PaymentNotifyRequest bad = PaymentNotifyRequest.builder()
                .tradeNo("T1").status("SUCCESS").amount(new BigDecimal("9.90")).sign("forged").build();

        assertThrows(BusinessException.class, () -> service.handleNotify(bad));
        verify(paymentMapper, never()).advanceStatus(any(), any(), any());
        verify(orderMapper, never()).markPaid(any(), any());
    }

    @Test
    @DisplayName("幂等：流水已 SUCCESS 时重复回调直接返回，不再改单")
    void notify_alreadyTerminal_idempotent() {
        when(paymentMapper.selectByTradeNo("T1")).thenReturn(payment("T1", 1001L, "SUCCESS"));

        service.handleNotify(signedNotify("T1", "SUCCESS", new BigDecimal("9.90")));

        verify(paymentMapper, never()).advanceStatus(any(), any(), any());
        verify(orderMapper, never()).markPaid(any(), any());
    }

    @Test
    @DisplayName("金额不一致 → 抛异常")
    void notify_amountMismatch_throws() {
        when(paymentMapper.selectByTradeNo("T1")).thenReturn(payment("T1", 1001L, "PENDING"));

        assertThrows(BusinessException.class,
                () -> service.handleNotify(signedNotify("T1", "SUCCESS", new BigDecimal("99.00"))));
        verify(orderMapper, never()).markPaid(any(), any());
    }

    @Test
    @DisplayName("回调失败：流水置 FAILED，不置订单 PAID")
    void notify_failed_noMarkPaid() {
        when(paymentMapper.selectByTradeNo("T1")).thenReturn(payment("T1", 1001L, "PENDING"));
        when(paymentMapper.advanceStatus(eq("T1"), eq("FAILED"), isNull())).thenReturn(1);

        service.handleNotify(signedNotify("T1", "FAILED", new BigDecimal("9.90")));

        verify(paymentMapper).advanceStatus(eq("T1"), eq("FAILED"), isNull());
        verify(orderMapper, never()).markPaid(any(), any());
    }

    // ── triggerMockCallback ──
    @Test
    @DisplayName("模拟收银台：未知交易 → 抛异常，不调度")
    void trigger_unknownTrade_throws() {
        when(paymentMapper.selectByTradeNo("X")).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.triggerMockCallback("X", true));
        verify(callbackScheduler, never()).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    @Test
    @DisplayName("模拟收银台：存在交易 → 调度延迟回调")
    void trigger_schedulesCallback() {
        when(paymentMapper.selectByTradeNo("T1")).thenReturn(payment("T1", 1001L, "PENDING"));

        service.triggerMockCallback("T1", true);

        verify(callbackScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
    }
}
