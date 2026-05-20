package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.CreateReplyRequest;
import com.twentyzhang.bluewhale.dto.CreateReviewRequest;
import com.twentyzhang.bluewhale.dto.ReviewResponse;
import com.twentyzhang.bluewhale.entity.Review;
import com.twentyzhang.bluewhale.mapper.ReviewMapper;
import com.twentyzhang.bluewhale.service.ReviewService;
import org.springframework.stereotype.Service;

@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {

    @Override
    public IPage<ReviewResponse> getProductReviews(Long productId, int page, int size) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Long createReview(Long userId, Long productId, CreateReviewRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Long replyToReview(Long userId, Long reviewId, CreateReplyRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
