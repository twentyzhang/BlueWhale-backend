package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CreateReplyRequest;
import com.twentyzhang.bluewhale.dto.CreateReviewRequest;
import com.twentyzhang.bluewhale.dto.ReviewReplyResponse;
import com.twentyzhang.bluewhale.dto.ReviewResponse;
import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.entity.OrderItem;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.Review;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.OrderItemMapper;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.mapper.ReviewMapper;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.service.ReviewService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.CacheKeys;
import com.twentyzhang.bluewhale.util.CacheUtil;
import com.twentyzhang.bluewhale.util.RedisUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {

    private final ProductMapper    productMapper;
    private final OrderMapper      orderMapper;
    private final OrderItemMapper  orderItemMapper;
    private final UserMapper       userMapper;
    private final CacheUtil        cacheUtil;
    private final RedisUtil        redisUtil;

    // ── 1. listReviews ────────────────────────────────────────────────────────

    @Override
    public IPage<ReviewResponse> getProductReviews(Long productId, int page, int size) {
        return cacheUtil.getOrLoad(CacheKeys.productReviews(productId, page, size),
                CacheKeys.PRODUCT_REVIEWS_TTL, CacheKeys.PRODUCT_REVIEWS_JITTER,
                () -> loadProductReviews(productId, page, size),
                new TypeReference<Page<ReviewResponse>>() {});
    }

    @SuppressWarnings("unchecked")
    private Page<ReviewResponse> loadProductReviews(Long productId, int page, int size) {
        // 无需登录。先校验商品存在。
        if (productMapper.selectById(productId) == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }

        // 查分页顶级评论（parentId IS NULL）
        IPage<Review> topPage = this.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getProductId, productId)
                        .isNull(Review::getParentId)
                        .orderByDesc(Review::getCreateTime));

        List<Review> topReviews = topPage.getRecords();
        if (topReviews.isEmpty()) {
            return (Page<ReviewResponse>) topPage.convert(r -> ReviewResponse.builder().build());
        }

        // 批量查本页所有顶级评论的回复（决策文档第 16 条）
        List<Long> topIds = topReviews.stream().map(Review::getId).collect(Collectors.toList());
        List<Review> replies = list(new LambdaQueryWrapper<Review>()
                .in(Review::getParentId, topIds)
                .orderByAsc(Review::getCreateTime));

        // 按 parentId 分组
        Map<Long, List<Review>> repliesByParent = replies.stream()
                .collect(Collectors.groupingBy(Review::getParentId));

        // 批量查涉及的所有用户昵称（评论者 + 回复者）
        Set<Long> userIds = new HashSet<>();
        topReviews.forEach(r -> userIds.add(r.getUserId()));
        replies.forEach(r -> userIds.add(r.getUserId()));
        Map<Long, String> nicknameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds)
                    .forEach(u -> nicknameMap.put(u.getId(), u.getNickname()));
        }

        return (Page<ReviewResponse>) topPage.convert(r -> ReviewResponse.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .userNickname(nicknameMap.get(r.getUserId()))
                .rating(r.getRating())
                .content(r.getContent())
                .createdAt(r.getCreateTime())
                .parentId(r.getParentId())
                .replies(repliesByParent.getOrDefault(r.getId(), List.of()).stream()
                        .map(reply -> ReviewReplyResponse.builder()
                                .id(reply.getId())
                                .userId(reply.getUserId())
                                .userNickname(nicknameMap.get(reply.getUserId()))
                                .content(reply.getContent())
                                .createdAt(reply.getCreateTime())
                                .build())
                        .collect(Collectors.toList()))
                .build());
    }

    // ── 2. createReview ───────────────────────────────────────────────────────

    @Override
    public Long createReview(Long userId, Long productId, CreateReviewRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        // 商品存在性校验
        if (productMapper.selectById(productId) == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商品不存在");
        }

        // 验证购买资格：订单存在、属于当前用户、状态为 COMPLETED
        Order order = orderMapper.selectById(request.getOrderId());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException("订单不存在或不属于当前用户");
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BusinessException("只有已完成的订单才能评价（当前状态：" + order.getStatus() + "）");
        }

        // 验证订单中包含该商品
        long itemCount = orderItemMapper.selectCount(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, request.getOrderId())
                .eq(OrderItem::getProductId, productId));
        if (itemCount == 0) {
            throw new BusinessException("该订单中不包含此商品");
        }

        // 重复评论校验（同一用户对同一订单中的同一商品只能评论一次）
        long existingCount = count(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getProductId, productId)
                .eq(Review::getOrderId, request.getOrderId())
                .isNull(Review::getParentId));
        if (existingCount > 0) {
            throw new BusinessException("您已对该订单中的此商品评论过");
        }

        Review review = Review.builder()
                .productId(productId)
                .userId(userId)
                .orderId(request.getOrderId())
                .rating(request.getRating())
                .content(request.getContent())
                .build();
        save(review);
        redisUtil.deleteByPrefix(CacheKeys.productReviewsPrefix(productId)); // 失效该商品的评论分页缓存
        return review.getId();
    }

    // ── 3. createReply ────────────────────────────────────────────────────────

    @Override
    public Long replyToReview(Long userId, Long reviewId, CreateReplyRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        Review parent = getById(reviewId);
        if (parent == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "评论不存在");
        }
        if (parent.getParentId() != null) {
            // 禁止对回复再回复，避免多级嵌套
            throw new BusinessException("不支持多级回复");
        }

        Review reply = Review.builder()
                .productId(parent.getProductId())
                .userId(userId)
                .parentId(reviewId)
                .content(request.getContent())
                // 回复无需 orderId 和 rating
                .build();
        save(reply);
        redisUtil.deleteByPrefix(CacheKeys.productReviewsPrefix(parent.getProductId())); // 失效该商品的评论分页缓存
        return reply.getId();
    }
}
