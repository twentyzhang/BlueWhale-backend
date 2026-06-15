package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 模拟收银台（开放）：扮演「用户在支付宝付钱」。触发延迟异步回调。
 * 真接支付宝时删除本控制器，由支付宝来调 /api/payments/notify。
 */
@RestController
@RequestMapping("/api/mock-pay")
@RequiredArgsConstructor
public class MockCashierController {

    private final PaymentService paymentService;

    @PostMapping("/{tradeNo}/success")
    public Result<Void> success(@PathVariable String tradeNo) {
        paymentService.triggerMockCallback(tradeNo, true);
        return Result.success();
    }

    @PostMapping("/{tradeNo}/fail")
    public Result<Void> fail(@PathVariable String tradeNo) {
        paymentService.triggerMockCallback(tradeNo, false);
        return Result.success();
    }
}
