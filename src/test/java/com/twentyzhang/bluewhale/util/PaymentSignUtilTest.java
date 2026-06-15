package com.twentyzhang.bluewhale.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentSignUtil")
class PaymentSignUtilTest {

    private static final String SECRET = "test-secret";

    @Test
    @DisplayName("sign 确定性：相同参数 + 密钥得到相同签名")
    void sign_deterministic() {
        String a = PaymentSignUtil.sign("T1", "SUCCESS", new BigDecimal("9.90"), SECRET);
        String b = PaymentSignUtil.sign("T1", "SUCCESS", new BigDecimal("9.90"), SECRET);
        assertEquals(a, b);
        assertFalse(a.isBlank());
    }

    @Test
    @DisplayName("verify：正确签名通过")
    void verify_validPasses() {
        String sign = PaymentSignUtil.sign("T1", "SUCCESS", new BigDecimal("9.90"), SECRET);
        assertTrue(PaymentSignUtil.verify("T1", "SUCCESS", new BigDecimal("9.90"), SECRET, sign));
    }

    @Test
    @DisplayName("verify：参数被篡改 → 验签失败")
    void verify_tamperedFails() {
        String sign = PaymentSignUtil.sign("T1", "SUCCESS", new BigDecimal("9.90"), SECRET);
        assertFalse(PaymentSignUtil.verify("T1", "SUCCESS", new BigDecimal("99.00"), SECRET, sign)); // 改金额
        assertFalse(PaymentSignUtil.verify("T1", "FAILED", new BigDecimal("9.90"), SECRET, sign));   // 改状态
        assertFalse(PaymentSignUtil.verify("T2", "SUCCESS", new BigDecimal("9.90"), SECRET, sign));   // 改交易号
    }

    @Test
    @DisplayName("verify：sign 为 null → false")
    void verify_nullSign() {
        assertFalse(PaymentSignUtil.verify("T1", "SUCCESS", new BigDecimal("9.90"), SECRET, null));
    }
}
