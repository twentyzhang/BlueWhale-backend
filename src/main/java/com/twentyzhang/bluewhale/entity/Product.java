package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;

    private Long categoryId;

    private String name;

    private BigDecimal price;

    private Integer stock;

    private String imageUrl;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号。库存扣减/调整通过自定义 SQL（version = version + 1 WHERE version = ?）维护，
     * 防止并发下单超卖。本项目未注册 OptimisticLockerInnerInterceptor，故普通 updateById 不参与
     * 版本校验，版本号仅由 ProductMapper 的乐观锁方法推进。
     */
    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
