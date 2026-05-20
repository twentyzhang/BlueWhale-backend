package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.CouponGroup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 可领取优惠券列表（remainCount > 0, startAt <= now, expireAt >= now）
 * 通过 Service 层 LambdaQueryWrapper 动态拼接，无需自定义方法。
 */
@Mapper
public interface CouponGroupMapper extends BaseMapper<CouponGroup> {
}
