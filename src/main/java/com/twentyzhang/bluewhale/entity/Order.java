package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long storeId;

    /** 订单状态：PENDING_PAYMENT / PAID / SHIPPED / COMPLETED / CANCELLED */
    private String status;

    /** 商品总金额（折扣前） */
    private BigDecimal totalAmount;

    /** 优惠折扣金额 */
    private BigDecimal discountAmount;

    /** 实付金额 */
    private BigDecimal payableAmount;

    /** 使用的优惠券 ID，未使用时为 null */
    private Long couponId;

    // ---------- 收货地址快照 ----------

    private String addrReceiverName;

    private String addrPhone;

    private String addrProvince;

    private String addrCity;

    private String addrDistrict;

    private String addrDetail;

    // ---------- 物流信息 ----------

    private String trackingNumber;

    private String carrier;

    // ---------- 各阶段时间节点 ----------

    private LocalDateTime paidAt;

    private LocalDateTime shippedAt;

    private LocalDateTime completedAt;

    private LocalDateTime cancelledAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
