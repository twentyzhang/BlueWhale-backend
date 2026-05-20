package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.CreateReplyRequest;
import com.twentyzhang.bluewhale.dto.CreateReviewRequest;
import com.twentyzhang.bluewhale.dto.IdResponse;
import com.twentyzhang.bluewhale.dto.ReviewResponse;
import com.twentyzhang.bluewhale.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/products/{productId}/reviews")
    public ApiResponse<IPage<ReviewResponse>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(reviewService.getProductReviews(productId, page, size));
    }

    // 需要鉴权 仅 Customer（需有该商品对应的已完成订单）
    @PostMapping("/api/products/{productId}/reviews")
    public ApiResponse<IdResponse> createReview(@PathVariable Long productId,
                                                 @RequestBody @Valid CreateReviewRequest request) {
        return ApiResponse.success(IdResponse.builder().id(reviewService.createReview(getCurrentUserId(), productId, request)).build());
    }

    // 需要鉴权 仅 Customer
    @PostMapping("/api/reviews/{reviewId}/replies")
    public ApiResponse<IdResponse> replyToReview(@PathVariable Long reviewId,
                                                  @RequestBody @Valid CreateReplyRequest request) {
        return ApiResponse.success(IdResponse.builder().id(reviewService.replyToReview(getCurrentUserId(), reviewId, request)).build());
    }

    private Long getCurrentUserId() {
        // TODO: 从 JWT Token 解析 userId
        return null;
    }
}
