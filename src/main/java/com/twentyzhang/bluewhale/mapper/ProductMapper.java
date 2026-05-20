package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品多条件搜索（keyword / categoryId / priceRange / storeId）
 * 均通过 Service 层构建 LambdaQueryWrapper 后调用 selectPage()，无需自定义方法。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
