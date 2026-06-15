package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    /** 按交易号查流水（幂等/查单用）。 */
    @Select("SELECT * FROM payment WHERE trade_no = #{tradeNo} AND deleted = 0")
    Payment selectByTradeNo(@Param("tradeNo") String tradeNo);

    /**
     * 原子推进流水状态：仅 PENDING 可推进到终态，防并发重复回调。
     * 返回 1=本次推进成功，0=已被并发回调抢先（调用方据此幂等返回）。
     */
    @Update("""
            UPDATE payment
            SET status = #{status}, paid_at = #{paidAt}
            WHERE trade_no = #{tradeNo} AND status = 'PENDING' AND deleted = 0
            """)
    int advanceStatus(@Param("tradeNo") String tradeNo,
                      @Param("status") String status,
                      @Param("paidAt") LocalDateTime paidAt);
}
