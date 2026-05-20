package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.dto.ClaimCouponResponse;
import com.twentyzhang.bluewhale.dto.MyCouponResponse;
import com.twentyzhang.bluewhale.entity.Coupon;

import java.util.List;

public interface CouponService extends IService<Coupon> {

    /**
     * 用户领取指定优惠券组中的一张优惠券。
     * 校验优惠券组存在、在有效期内、remainCount > 0，并防止重复领取同一组。
     * 成功后 remainCount 原子减 1。
     * 对应 POST /api/coupon-groups/{groupId}/claim
     */
    ClaimCouponResponse claimCoupon(Long userId, Long groupId);

    /**
     * 获取当前用户的优惠券列表，可按状态筛选（UNUSED / USED / EXPIRED）。
     * status 为 null 时返回全部。
     * 对应 GET /api/coupons/mine
     */
    List<MyCouponResponse> getMyCoupons(Long userId, String status);
}
