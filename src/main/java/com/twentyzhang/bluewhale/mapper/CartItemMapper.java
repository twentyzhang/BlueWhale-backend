package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车详情（productName / price / stock）由 Service 层用
 * productMapper.selectBatchIds() 二次查询后组装 VO，无需 JOIN 方法。
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
