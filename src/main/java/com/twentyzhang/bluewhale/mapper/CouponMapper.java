package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;

/**
 * GET /api/coupons/mine 的 groupName / type / value 等字段
 * 来自 coupon_group 表，由 Service 层用 couponGroupMapper.selectBatchIds()
 * 二次查询后组装响应 VO，无需 JOIN 方法。
 */
@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {
}
