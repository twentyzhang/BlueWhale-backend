package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.Review;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论列表分页：Service 先查顶级评论（parentId IS NULL），
 * 再用 LambdaQueryWrapper.in(Review::getParentId, topIds) 批量查回复，
 * 无需 JOIN 方法。userNickname 由 Service 层从 userMapper 补充。
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
}
