package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CreateReplyRequest;
import com.twentyzhang.bluewhale.dto.CreateReviewRequest;
import com.twentyzhang.bluewhale.dto.IdResponse;
import com.twentyzhang.bluewhale.dto.ReviewResponse;
import com.twentyzhang.bluewhale.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public Result<IPage<ReviewResponse>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(reviewService.getProductReviews(productId, page, size));
    }

    // 需要鉴权 仅 Customer（需有该商品对应的已完成订单）
    @PostMapping("/api/products/{productId}/reviews")
    public Result<IdResponse> createReview(@PathVariable Long productId,
                                            @RequestBody @Valid CreateReviewRequest request) {
        return Result.success(IdResponse.builder().id(reviewService.createReview(currentUser().userId(), productId, request)).build());
    }

    // 需要鉴权 仅 Customer
    @PostMapping("/api/reviews/{reviewId}/replies")
    public Result<IdResponse> replyToReview(@PathVariable Long reviewId,
                                             @RequestBody @Valid CreateReplyRequest request) {
        return Result.success(IdResponse.builder().id(reviewService.replyToReview(currentUser().userId(), reviewId, request)).build());
    }

    private AuthUser currentUser() {
        return (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
