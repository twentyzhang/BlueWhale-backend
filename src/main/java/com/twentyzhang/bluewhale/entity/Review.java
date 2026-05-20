package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 商品评论（支持楼层回复）
 * schema 补充：review 表无 updated_at 列，updateTime 标注 exist=false，如需持久化请在 schema.sql 中补充。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Long userId;

    /**
     * 关联订单 ID。
     * 一级评论（parentId = null）必填，用于校验购买资格；回复评论可为 null。
     */
    private Long orderId;

    /** 父评论 ID，null 表示一级评价；有值表示回复 */
    private Long parentId;

    /** 评分 1-5，仅一级评价有值，回复时为 null */
    private Integer rating;

    private String content;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** review 表暂无 updated_at 列，仅在内存中维护 */
    @TableField(exist = false)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
