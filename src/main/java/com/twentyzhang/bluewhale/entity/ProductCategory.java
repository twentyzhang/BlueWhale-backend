package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 商品分类（支持自关联嵌套）
 * schema 补充：product_category 表需添加 created_at、updated_at 列
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("product_category")
public class ProductCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 父分类 ID，null 表示顶级分类 */
    private Long parentId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
