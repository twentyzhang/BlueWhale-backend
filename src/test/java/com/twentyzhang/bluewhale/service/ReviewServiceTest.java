package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CreateReplyRequest;
import com.twentyzhang.bluewhale.dto.CreateReviewRequest;
import com.twentyzhang.bluewhale.dto.ReviewResponse;
import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.Review;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.OrderItemMapper;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.mapper.ReviewMapper;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.service.impl.ReviewServiceImpl;
import com.twentyzhang.bluewhale.util.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ReviewService")
class ReviewServiceTest extends BaseServiceTest {

    // ReviewMapper 作为 baseMapper 单独处理，其余通过 @RequiredArgsConstructor 注入
    @Mock private ReviewMapper    reviewMapper;
    @Mock private ProductMapper   productMapper;
    @Mock private OrderMapper     orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    // UserMapper 虽不在题目列表中，但 getProductReviews 需用它批量查昵称，否则正常用例 NPE
    @Mock private UserMapper      userMapper;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    // disambiguation helper（BaseMapper 3.5.9 insert(T)/insert(Collection) 重载）
    private static Review anyReview() { return ArgumentMatchers.any(Review.class); }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reviewService, "baseMapper", reviewMapper);
    }

    // ── 构建辅助 ──────────────────────────────────────────────────────────────

    private static Review topReview(Long id, Long userId, int rating, String content) {
        return Review.builder()
                .id(id).productId(101L).userId(userId).orderId(1001L)
                .parentId(null).rating(rating).content(content)
                .build();
    }

    private static Review reply(Long id, Long userId, Long parentId, String content) {
        return Review.builder()
                .id(id).productId(101L).userId(userId)
                .parentId(parentId).rating(null).content(content)
                .build();
    }

    private static User user(Long id, String nickname) {
        return User.builder().id(id).nickname(nickname).build();
    }

    private static CreateReviewRequest reviewReq(Long orderId, Integer rating, String content) {
        CreateReviewRequest r = new CreateReviewRequest();
        r.setOrderId(orderId);
        r.setRating(rating);
        r.setContent(content);
        return r;
    }

    private static CreateReplyRequest replyReq(String content) {
        CreateReplyRequest r = new CreateReplyRequest();
        r.setContent(content);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static IPage<Review> pageOf(List<Review> records) {
        Page<Review> page = new Page<>(1, 10);
        page.setRecords(records);
        page.setTotal(records.size());
        return page;
    }

    // ── 1. listReviews（getProductReviews） ────────────────────────────────────

    @Nested
    @DisplayName("getProductReviews")
    class GetProductReviewsTests {

        @Test
        @DisplayName("正常返回分页评论列表，每条顶级评论附带其回复")
        void success_withReplies() {
            when(productMapper.selectById(101L)).thenReturn(Product.builder().id(101L).build());
            when(reviewMapper.selectPage(any(), any()))
                    .thenReturn(pageOf(List.of(topReview(201L, 1L, 5, "醋味纯正，物超所值！"))));
            // 顶级评论的回复
            when(reviewMapper.selectList(any()))
                    .thenReturn(List.of(reply(205L, 2L, 201L, "同感，一直回购")));
            // 评论者 + 回复者昵称
            when(userMapper.selectBatchIds(any()))
                    .thenReturn(List.of(user(1L, "张三"), user(2L, "李四")));

            IPage<ReviewResponse> resp = reviewService.getProductReviews(101L, 1, 10);

            assertEquals(1, resp.getRecords().size());
            ReviewResponse top = resp.getRecords().get(0);
            assertEquals(201L, top.getId());
            assertEquals("张三", top.getUserNickname());
            assertEquals(5, top.getRating());
            assertNull(top.getParentId());

            assertEquals(1, top.getReplies().size());
            assertEquals(205L, top.getReplies().get(0).getId());
            assertEquals("李四", top.getReplies().get(0).getUserNickname());
            assertEquals("同感，一直回购", top.getReplies().get(0).getContent());
        }

        @Test
        @DisplayName("商品不存在时抛出 BusinessException（code 404）")
        void productNotFound_throws404() {
            when(productMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.getProductReviews(999L, 1, 10));

            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verify(reviewMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("无评论时返回空列表（不再查回复和昵称）")
        void noReviews_returnsEmpty() {
            when(productMapper.selectById(101L)).thenReturn(Product.builder().id(101L).build());
            when(reviewMapper.selectPage(any(), any())).thenReturn(pageOf(List.of()));

            IPage<ReviewResponse> resp = reviewService.getProductReviews(101L, 1, 10);

            assertTrue(resp.getRecords().isEmpty());
            verify(reviewMapper, never()).selectList(any());
            verify(userMapper, never()).selectBatchIds(any());
        }
    }

    // ── 2. createReview ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("createReview")
    class CreateReviewTests {

        /** 商品存在 + 订单合法（属当前用户、COMPLETED、含该商品）的公共 stub。 */
        private void stubValidPurchase() {
            when(productMapper.selectById(101L)).thenReturn(Product.builder().id(101L).build());
            when(orderMapper.selectById(1001L))
                    .thenReturn(Order.builder().id(1001L).userId(1L).status("COMPLETED").build());
            when(orderItemMapper.selectCount(any())).thenReturn(1L);
        }

        @Test
        @DisplayName("正常发表评论，返回新建评论 ID，并写入正确字段")
        void success() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            stubValidPurchase();
            when(reviewMapper.selectCount(any())).thenReturn(0L); // 无重复评论
            doAnswer(inv -> { ((Review) inv.getArgument(0)).setId(201L); return 1; })
                    .when(reviewMapper).insert(anyReview());

            Long id = reviewService.createReview(1L, 101L, reviewReq(1001L, 5, "醋味纯正"));

            assertEquals(201L, id);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewMapper).insert((Review) captor.capture());
            Review saved = captor.getValue();
            assertEquals(101L, saved.getProductId());
            assertEquals(1L,   saved.getUserId());
            assertEquals(1001L, saved.getOrderId());
            assertEquals(5,    saved.getRating());
            assertEquals("醋味纯正", saved.getContent());
            assertNull(saved.getParentId()); // 顶级评价
        }

        @Test
        @DisplayName("商品不存在时抛出 BusinessException（code 404）")
        void productNotFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(productMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.createReview(1L, 999L, reviewReq(1001L, 5, "好评")));

            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verify(orderMapper, never()).selectById(any());
            verify(reviewMapper, never()).insert(anyReview());
        }

        @Test
        @DisplayName("订单不属于当前用户时抛出 BusinessException")
        void orderNotOwned_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(productMapper.selectById(101L)).thenReturn(Product.builder().id(101L).build());
            // 订单归属 userId=2，当前用户为 1
            when(orderMapper.selectById(1001L))
                    .thenReturn(Order.builder().id(1001L).userId(2L).status("COMPLETED").build());

            assertThrows(BusinessException.class,
                    () -> reviewService.createReview(1L, 101L, reviewReq(1001L, 5, "好评")));
            verify(orderItemMapper, never()).selectCount(any());
            verify(reviewMapper, never()).insert(anyReview());
        }

        @Test
        @DisplayName("订单状态不为 COMPLETED 时抛出 BusinessException")
        void orderNotCompleted_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(productMapper.selectById(101L)).thenReturn(Product.builder().id(101L).build());
            when(orderMapper.selectById(1001L))
                    .thenReturn(Order.builder().id(1001L).userId(1L).status("PAID").build());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.createReview(1L, 101L, reviewReq(1001L, 5, "好评")));
            assertTrue(ex.getMessage().contains("PAID"),
                    "错误消息应含当前状态，实际：" + ex.getMessage());
            verify(orderItemMapper, never()).selectCount(any());
            verify(reviewMapper, never()).insert(anyReview());
        }

        @Test
        @DisplayName("订单中不包含该商品时抛出 BusinessException")
        void orderMissingProduct_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(productMapper.selectById(101L)).thenReturn(Product.builder().id(101L).build());
            when(orderMapper.selectById(1001L))
                    .thenReturn(Order.builder().id(1001L).userId(1L).status("COMPLETED").build());
            when(orderItemMapper.selectCount(any())).thenReturn(0L); // 订单不含该商品

            assertThrows(BusinessException.class,
                    () -> reviewService.createReview(1L, 101L, reviewReq(1001L, 5, "好评")));
            verify(reviewMapper, never()).selectCount(any()); // 未到重复校验
            verify(reviewMapper, never()).insert(anyReview());
        }

        @Test
        @DisplayName("重复评论时抛出 BusinessException")
        void duplicateReview_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            stubValidPurchase();
            when(reviewMapper.selectCount(any())).thenReturn(1L); // 已存在评论

            assertThrows(BusinessException.class,
                    () -> reviewService.createReview(1L, 101L, reviewReq(1001L, 5, "好评")));
            verify(reviewMapper, never()).insert(anyReview());
        }

        @Test
        @DisplayName("非 Customer 调用时抛出 BusinessException（code 403）")
        void nonCustomer_throws403() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.createReview(10L, 101L, reviewReq(1001L, 5, "好评")));

            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verify(productMapper, never()).selectById(any());
            verify(reviewMapper, never()).insert(anyReview());
        }
    }

    // ── 3. createReply（replyToReview） ────────────────────────────────────────

    @Nested
    @DisplayName("replyToReview")
    class ReplyToReviewTests {

        @Test
        @DisplayName("正常回复顶级评论，返回新建回复 ID，rating/orderId 为 null")
        void success() {
            mockAuthUser(2L, AuthUtil.ROLE_CUSTOMER, null);
            when(reviewMapper.selectById(201L)).thenReturn(topReview(201L, 1L, 5, "好评"));
            doAnswer(inv -> { ((Review) inv.getArgument(0)).setId(205L); return 1; })
                    .when(reviewMapper).insert(anyReview());

            Long id = reviewService.replyToReview(2L, 201L, replyReq("同感，一直回购"));

            assertEquals(205L, id);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            verify(reviewMapper).insert((Review) captor.capture());
            Review saved = captor.getValue();
            assertEquals(201L, saved.getParentId());   // 挂在顶级评论下
            assertEquals(101L, saved.getProductId());  // 继承父评论商品
            assertEquals(2L,   saved.getUserId());
            assertEquals("同感，一直回购", saved.getContent());
            assertNull(saved.getRating());
            assertNull(saved.getOrderId());
        }

        @Test
        @DisplayName("评论不存在时抛出 BusinessException（code 404）")
        void reviewNotFound_throws404() {
            mockAuthUser(2L, AuthUtil.ROLE_CUSTOMER, null);
            when(reviewMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.replyToReview(2L, 999L, replyReq("回复")));

            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verify(reviewMapper, never()).insert(anyReview());
        }

        @Test
        @DisplayName("对非顶级评论回复时抛出 BusinessException（不支持多级回复）")
        void replyToReply_throws() {
            mockAuthUser(2L, AuthUtil.ROLE_CUSTOMER, null);
            // 目标本身是回复（parentId 不为 null）
            when(reviewMapper.selectById(205L)).thenReturn(reply(205L, 3L, 201L, "回复内容"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.replyToReview(2L, 205L, replyReq("再回复")));

            assertTrue(ex.getMessage().contains("不支持多级回复"),
                    "错误消息应提示不支持多级回复，实际：" + ex.getMessage());
            verify(reviewMapper, never()).insert(anyReview());
        }

        @Test
        @DisplayName("非 Customer 调用时抛出 BusinessException（code 403）")
        void nonCustomer_throws403() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reviewService.replyToReview(10L, 201L, replyReq("回复")));

            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verify(reviewMapper, never()).selectById(any());
            verify(reviewMapper, never()).insert(anyReview());
        }
    }
}
