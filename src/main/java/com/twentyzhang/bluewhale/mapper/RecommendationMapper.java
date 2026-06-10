package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.ProductSimilarity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface RecommendationMapper extends BaseMapper<ProductSimilarity> {

    /**
     * 原始交互：用户 × 商品 × 订单状态。权重在服务层按状态映射并取最大（便于调参/单测）。
     * userId 为 null 时取全量（重建用）；非 null 时取单用户（个性化用）。
     * order_item 无 deleted 列；orders 自定义 SQL 需显式 deleted = 0。
     */
    @Select("""
            SELECT o.user_id AS userId, oi.product_id AS productId, o.status AS status
            FROM orders o
            JOIN order_item oi ON oi.order_id = o.id
            WHERE o.deleted = 0
              AND (#{userId} IS NULL OR o.user_id = #{userId})
            """)
    List<Map<String, Object>> selectInteractions(@Param("userId") Long userId);

    /**
     * 用户对商品的评分（一级评价，rating 非空），供重建时按评分加权兴趣信号。
     * 同一用户对同一商品可能有多条（多笔订单各评一次），服务层取最高分。
     */
    @Select("""
            SELECT user_id AS userId, product_id AS productId, rating
            FROM review
            WHERE deleted = 0 AND parent_id IS NULL AND rating IS NOT NULL
            """)
    List<Map<String, Object>> selectUserProductRatings();

    /** 某商品的 Top 相似商品（含 score），按相似度降序。 */
    @Select("""
            SELECT product_id AS productId,
                   similar_product_id AS similarProductId,
                   score
            FROM product_similarity
            WHERE product_id = #{productId}
            ORDER BY score DESC
            LIMIT #{limit}
            """)
    List<ProductSimilarity> selectTopSimilar(@Param("productId") Long productId, @Param("limit") int limit);

    /** 全站热销商品 ID，按成交件数（SUM quantity）降序。仅统计真实成交状态。 */
    @Select("""
            SELECT oi.product_id
            FROM order_item oi
            JOIN orders o ON oi.order_id = o.id
            WHERE o.deleted = 0
              AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
            GROUP BY oi.product_id
            ORDER BY SUM(oi.quantity) DESC
            LIMIT #{limit}
            """)
    List<Long> selectGlobalHotProductIds(@Param("limit") int limit);

    /** 指定类目的热销商品 ID，口径同上。 */
    @Select("""
            SELECT oi.product_id
            FROM order_item oi
            JOIN orders o   ON oi.order_id = o.id
            JOIN product p  ON oi.product_id = p.id
            WHERE o.deleted = 0
              AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
              AND p.category_id = #{categoryId}
            GROUP BY oi.product_id
            ORDER BY SUM(oi.quantity) DESC
            LIMIT #{limit}
            """)
    List<Long> selectCategoryHotProductIds(@Param("categoryId") Long categoryId, @Param("limit") int limit);

    /** 清空相似度表（全量重建第一步）。 */
    @Delete("DELETE FROM product_similarity")
    int deleteAllSimilarities();
}
