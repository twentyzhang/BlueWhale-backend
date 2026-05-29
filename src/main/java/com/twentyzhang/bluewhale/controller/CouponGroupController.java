package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.ClaimCouponResponse;
import com.twentyzhang.bluewhale.dto.CouponGroupResponse;
import com.twentyzhang.bluewhale.dto.CreateCouponGroupRequest;
import com.twentyzhang.bluewhale.dto.IdResponse;
import com.twentyzhang.bluewhale.service.CouponGroupService;
import com.twentyzhang.bluewhale.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public Result<IPage<CouponGroupResponse>> getAvailableCouponGroups(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(couponGroupService.getAvailableCouponGroups(page, size));
    }

    // 需要鉴权 仅 Customer
    @PostMapping("/api/coupon-groups/{groupId}/claim")
    public Result<ClaimCouponResponse> claimCoupon(@PathVariable Long groupId) {
        return Result.success(couponService.claimCoupon(currentUser().userId(), groupId));
    }

    // 需要鉴权 仅 Admin
    @PostMapping("/api/coupon-groups")
    public Result<IdResponse> createGlobalCouponGroup(@RequestBody @Valid CreateCouponGroupRequest request) {
        return Result.success(IdResponse.builder().id(couponGroupService.createGlobalCouponGroup(request)).build());
    }

    // 需要鉴权 Staff（本店券）或 Admin（全局券）
    @DeleteMapping("/api/coupon-groups/{groupId}")
    public Result<Void> deleteCouponGroup(@PathVariable Long groupId) {
        AuthUser user = currentUser();
        couponGroupService.deleteCouponGroup(user.storeId(), user.role(), groupId);
        return Result.success();
    }

    // 需要鉴权 仅 Staff（本店）
    @PostMapping("/api/stores/{storeId}/coupon-groups")
    public Result<IdResponse> createStoreCouponGroup(@PathVariable Long storeId,
                                                      @RequestBody @Valid CreateCouponGroupRequest request) {
        return Result.success(IdResponse.builder().id(couponGroupService.createStoreCouponGroup(storeId, request)).build());
    }

    private AuthUser currentUser() {
        return (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
