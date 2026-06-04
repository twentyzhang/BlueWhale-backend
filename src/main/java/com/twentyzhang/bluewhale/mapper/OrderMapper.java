package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 查询所有 created_at 早于 {@code expireTime} 且状态为 PENDING_PAYMENT 的未支付订单。
     * 供未支付订单自动取消的定时任务使用。
     * <p>自定义 SQL 不会自动追加逻辑删除条件，故显式 {@code deleted = 0}；
     * {@code created_at} 别名为 {@code createTime} 以匹配实体字段（见决策第 11 条）。
     */
    @Select("""
            SELECT id, user_id, store_id, status, coupon_id,
                   created_at AS createTime
            FROM orders
            WHERE status = 'PENDING_PAYMENT'
              AND created_at < #{expireTime}
              AND deleted = 0
            """)
    List<Order> selectExpiredUnpaidOrders(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 按状态分组统计订单数。
     * 用于 GET /api/stores/{storeId}/reports/orders 和 GET /api/admin/reports/orders 中
     * 的 statusBreakdown 字段。
     * storeId 为 null 时统计全局数据。
     */
    @Select("""
            SELECT status, COUNT(*) AS cnt
            FROM orders
            WHERE deleted = 0
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND (#{startDate} IS NULL OR DATE(created_at) >= #{startDate})
              AND (#{endDate}   IS NULL OR DATE(created_at) <= #{endDate})
            GROUP BY status
            """)
    List<Map<String, Object>> selectStatusBreakdown(
            @Param("storeId")    Long storeId,
            @Param("startDate")  LocalDate startDate,
            @Param("endDate")    LocalDate endDate
    );

    /**
     * 按日期分组统计已完成订单的收入和笔数。
     * 用于报表接口中的 dailyRevenue 数组。
     * storeId 为 null 时统计全局数据。
     */
    @Select("""
            SELECT DATE(created_at) AS date,
                   SUM(payable_amount) AS revenue,
                   COUNT(*) AS orders
            FROM orders
            WHERE deleted = 0
              AND status = 'COMPLETED'
              AND (#{storeId} IS NULL OR store_id = #{storeId})
              AND (#{startDate} IS NULL OR DATE(created_at) >= #{startDate})
              AND (#{endDate}   IS NULL OR DATE(created_at) <= #{endDate})
            GROUP BY DATE(created_at)
            ORDER BY date ASC
            """)
    List<Map<String, Object>> selectDailyRevenue(
            @Param("storeId")    Long storeId,
            @Param("startDate")  LocalDate startDate,
            @Param("endDate")    LocalDate endDate
    );

    /**
     * 按商店分组统计已完成订单的收入和笔数。
     * 仅用于 GET /api/admin/reports/orders 的 storeBreakdown 字段。
     */
    @Select("""
            SELECT o.store_id   AS storeId,
                   s.name       AS storeName,
                   COUNT(*)     AS orders,
                   SUM(o.payable_amount) AS revenue
            FROM orders o
            JOIN store s ON o.store_id = s.id AND s.deleted = 0
            WHERE o.deleted = 0
              AND o.status = 'COMPLETED'
              AND (#{startDate} IS NULL OR DATE(o.created_at) >= #{startDate})
              AND (#{endDate}   IS NULL OR DATE(o.created_at) <= #{endDate})
            GROUP BY o.store_id, s.name
            """)
    List<Map<String, Object>> selectStoreBreakdown(
            @Param("startDate")  LocalDate startDate,
            @Param("endDate")    LocalDate endDate
    );
}
