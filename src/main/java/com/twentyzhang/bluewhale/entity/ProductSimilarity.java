package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品相似度（item-based CF 离线预计算结果）。
 * 派生数据，由 RecommendationService.rebuildAll() 全量重建。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("product_similarity")
public class ProductSimilarity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Long similarProductId;

    /** 加权余弦相似度，0~1 */
    private BigDecimal score;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
