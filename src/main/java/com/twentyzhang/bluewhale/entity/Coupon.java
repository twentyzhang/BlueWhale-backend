package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 优惠券实例
 * schema 补充：coupon 表无 updated_at 列，updateTime 标注 exist=false，如需持久化请在 schema.sql 中补充。
 * createTime 复用 claimed_at 列（领取即创建）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("coupon")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private Long userId;

    /** 状态：UNUSED / USED / EXPIRED */
    private String status;

    /** 使用时间，未使用时为 null */
    private LocalDateTime usedAt;

    /** 使用于哪个订单，未使用时为 null */
    private Long orderId;

    /** 领取时间，复用 claimed_at 列作为创建时间 */
    @TableField(value = "claimed_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** coupon 表暂无 updated_at 列，仅在内存中维护 */
    @TableField(exist = false)
    private LocalDateTime updateTime;
}
