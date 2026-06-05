package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 订单-优惠券关联（支持一单多券叠加）。
 * 一笔订单使用的每张券各一行，取消/退款恢复优惠券时以本表为准。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_coupon")
public class OrderCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long couponId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
