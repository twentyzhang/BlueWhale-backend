package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.payment.PaymentNotifyRequest;
import com.twentyzhang.bluewhale.dto.payment.PaymentStatusResponse;
import com.twentyzhang.bluewhale.service.PaymentService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 支付回调（webhook，开放，靠 HMAC 验签）。真支付宝接入缝。 */
    @PostMapping("/notify")
    public Result<Void> notify(@RequestBody PaymentNotifyRequest request) {
        paymentService.handleNotify(request);
        return Result.success();
    }

    /** 查单（Customer 本人订单），供前端轮询。 */
    @GetMapping("/{tradeNo}")
    public Result<PaymentStatusResponse> query(@PathVariable String tradeNo) {
        AuthUser user = AuthUtil.getCurrentUser();
        return Result.success(paymentService.queryPayment(user.userId(), tradeNo));
    }
}
