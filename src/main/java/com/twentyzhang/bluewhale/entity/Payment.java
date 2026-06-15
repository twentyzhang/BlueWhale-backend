package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 交易号（幂等键），每次发起支付生成 */
    private String tradeNo;

    /** 支付金额（订单实付额快照） */
    private BigDecimal amount;

    /** PENDING / SUCCESS / FAILED */
    private String status;

    /** 渠道：MOCK（未来 ALIPAY） */
    private String channel;

    private LocalDateTime paidAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
