package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品多条件搜索（keyword / categoryId / priceRange / storeId）
 * 均通过 Service 层构建 LambdaQueryWrapper 后调用 selectPage()，无需自定义方法。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 乐观锁扣减库存：仅当版本号未变且库存充足时才扣减，并使版本号 +1。
     * 返回受影响行数：1 表示成功，0 表示版本被并发修改或库存不足（调用方据此重试/报错）。
     * 防并发超卖的核心是 {@code stock >= #{quantity}} 原子条件。
     */
    @Update("""
            UPDATE product
            SET stock = stock - #{quantity}, version = version + 1
            WHERE id = #{id} AND version = #{version} AND stock >= #{quantity}
              AND deleted = 0
            """)
    int updateStockWithVersion(@Param("id") Long id,
                               @Param("quantity") int quantity,
                               @Param("version") Integer version);

    /**
     * 乐观锁将库存设为指定绝对值（用于 Staff 手动入库/出库调整）：仅当版本号未变时才更新并 +1。
     * 返回受影响行数：1 成功，0 表示版本被并发修改（调用方据此重试）。
     * 出库的库存下限由 Service 层在计算 newStock 前校验。
     */
    @Update("""
            UPDATE product
            SET stock = #{stock}, version = version + 1
            WHERE id = #{id} AND version = #{version}
              AND deleted = 0
            """)
    int updateStockTo(@Param("id") Long id,
                      @Param("stock") int stock,
                      @Param("version") Integer version);

    /**
     * 原子增加库存（取消/退款恢复库存用）：{@code stock = stock + #{quantity}}，无需版本校验。
     * 加库存恒安全，用原子自增替代「读-改-写」，消除并发丢失更新窗口。
     * 同步推进 version 以与其他库存变更保持一致。逻辑删除的商品（deleted=1）返回 0 行、自动跳过。
     */
    @Update("""
            UPDATE product
            SET stock = stock + #{quantity}, version = version + 1
            WHERE id = #{id} AND deleted = 0
            """)
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
