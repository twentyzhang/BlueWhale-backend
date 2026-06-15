package com.twentyzhang.bluewhale.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 支付回调签名工具（HMAC-SHA256，模拟支付宝 RSA2 验签）。
 * 参数按固定字典序拼串后用共享密钥计算 HMAC；防止伪造 notify 把订单刷成已支付。
 */
public final class PaymentSignUtil {

    private PaymentSignUtil() {}

    /** 按固定顺序 amount&status&tradeNo 拼串后 HMAC-SHA256。 */
    public static String sign(String tradeNo, String status, BigDecimal amount, String secret) {
        String raw = "amount=" + amount.toPlainString() + "&status=" + status + "&tradeNo=" + tradeNo;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("支付签名计算失败: " + e.getMessage(), e);
        }
    }

    /** 用同样规则重算并比对（常量时间比较非必需，本项目从简）。 */
    public static boolean verify(String tradeNo, String status, BigDecimal amount, String secret, String sign) {
        return sign != null && sign.equals(sign(tradeNo, status, amount, secret));
    }
}
