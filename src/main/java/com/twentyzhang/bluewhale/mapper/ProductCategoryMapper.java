package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.ProductCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类树由 Service 层调用 selectList() 后递归组装，无需自定义查询。
 */
@Mapper
public interface ProductCategoryMapper extends BaseMapper<ProductCategory> {
}
