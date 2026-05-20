package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.MyCouponResponse;
import com.twentyzhang.bluewhale.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    // 需要鉴权 仅 Customer
    @GetMapping("/mine")
    public ApiResponse<List<MyCouponResponse>> getMyCoupons(
            @RequestParam(required = false) String status) {
        return ApiResponse.success(couponService.getMyCoupons(getCurrentUserId(), status));
    }

    private Long getCurrentUserId() {
        // TODO: 从 JWT Token 解析 userId
        return null;
    }
}
