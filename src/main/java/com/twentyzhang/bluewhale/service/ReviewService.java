package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.dto.CreateReplyRequest;
import com.twentyzhang.bluewhale.dto.CreateReviewRequest;
import com.twentyzhang.bluewhale.dto.ReviewResponse;
import com.twentyzhang.bluewhale.entity.Review;

public interface ReviewService extends IService<Review> {

    /**
     * 分页获取指定商品的评论列表（仅顶级评论分页，每条评论附带其回复列表）。
     * 对应 GET /api/products/{productId}/reviews
     */
    IPage<ReviewResponse> getProductReviews(Long productId, int page, int size);

    /**
     * 发表商品评价。校验用户对应订单状态为 COMPLETED 且未对该商品重复评价。
     * 对应 POST /api/products/{productId}/reviews
     *
     * @return 新建评论 ID
     */
    Long createReview(Long userId, Long productId, CreateReviewRequest request);

    /**
     * 回复指定评论。无需购买限制，任意已登录用户均可回复。
     * 对应 POST /api/reviews/{reviewId}/replies
     *
     * @return 新建回复 ID
     */
    Long replyToReview(Long userId, Long reviewId, CreateReplyRequest request);
}
