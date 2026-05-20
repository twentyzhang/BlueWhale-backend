package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("coupon_group")
public class CouponGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属商店 ID，null 表示全局优惠券 */
    private Long storeId;

    private String name;

    /** 类型：DISCOUNT（折扣券）/ AMOUNT_OFF（满减券） */
    private String type;

    /** 折扣券：折扣率（如 0.8 表示八折）；满减券：减免金额（元） */
    private BigDecimal value;

    /** 最低使用金额，0 表示无门槛 */
    private BigDecimal minOrderAmount;

    private Integer totalCount;

    private Integer remainCount;

    private LocalDateTime startAt;

    private LocalDateTime expireAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
