package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.ClaimCouponResponse;
import com.twentyzhang.bluewhale.dto.CouponGroupResponse;
import com.twentyzhang.bluewhale.dto.CreateCouponGroupRequest;
import com.twentyzhang.bluewhale.dto.IdResponse;
import com.twentyzhang.bluewhale.service.CouponGroupService;
import com.twentyzhang.bluewhale.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CouponGroupController {

    private final CouponGroupService couponGroupService;
    private final CouponService couponService;

    @GetMapping("/api/coupon-groups")
    public ApiResponse<IPage<CouponGroupResponse>> getAvailableCouponGroups(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(couponGroupService.getAvailableCouponGroups(page, size));
    }

    // 需要鉴权 仅 Customer
    @PostMapping("/api/coupon-groups/{groupId}/claim")
    public ApiResponse<ClaimCouponResponse> claimCoupon(@PathVariable Long groupId) {
        return ApiResponse.success(couponService.claimCoupon(getCurrentUserId(), groupId));
    }

    // 需要鉴权 仅 Admin
    @PostMapping("/api/coupon-groups")
    public ApiResponse<IdResponse> createGlobalCouponGroup(@RequestBody @Valid CreateCouponGroupRequest request) {
        return ApiResponse.success(IdResponse.builder().id(couponGroupService.createGlobalCouponGroup(request)).build());
    }

    // 需要鉴权 Staff（本店券）或 Admin（全局券）
    @DeleteMapping("/api/coupon-groups/{groupId}")
    public ApiResponse<Void> deleteCouponGroup(@PathVariable Long groupId) {
        couponGroupService.deleteCouponGroup(getCurrentUserStoreId(), getCurrentUserRole(), groupId);
        return ApiResponse.success();
    }

    // 需要鉴权 仅 Staff（本店）
    @PostMapping("/api/stores/{storeId}/coupon-groups")
    public ApiResponse<IdResponse> createStoreCouponGroup(@PathVariable Long storeId,
                                                           @RequestBody @Valid CreateCouponGroupRequest request) {
        return ApiResponse.success(IdResponse.builder().id(couponGroupService.createStoreCouponGroup(storeId, request)).build());
    }

    private Long getCurrentUserId() {
        // TODO: 从 JWT Token 解析 userId
        return null;
    }

    private Long getCurrentUserStoreId() {
        // TODO: 从 JWT Token 解析 storeId（Staff 专用）
        return null;
    }

    private String getCurrentUserRole() {
        // TODO: 从 JWT Token 解析 role
        return null;
    }
}
