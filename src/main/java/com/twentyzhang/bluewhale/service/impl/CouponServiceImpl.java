package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.ClaimCouponResponse;
import com.twentyzhang.bluewhale.dto.MyCouponResponse;
import com.twentyzhang.bluewhale.entity.Coupon;
import com.twentyzhang.bluewhale.mapper.CouponMapper;
import com.twentyzhang.bluewhale.service.CouponService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements CouponService {

    @Override
    public ClaimCouponResponse claimCoupon(Long userId, Long groupId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public List<MyCouponResponse> getMyCoupons(Long userId, String status) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
