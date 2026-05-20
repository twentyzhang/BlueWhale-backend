package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单条目（含商品快照）
 * schema 补充：order_item 表需添加 created_at、updated_at 列。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long productId;

    /** 商品名称快照，防止商品改名后历史订单展示异常 */
    private String productName;

    /** 商品图片快照 */
    private String productImage;

    /** 下单时单价快照 */
    private BigDecimal unitPrice;

    private Integer quantity;

    /** 小计 = unitPrice × quantity */
    private BigDecimal subtotal;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
