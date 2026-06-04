package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.ClaimCouponResponse;
import com.twentyzhang.bluewhale.dto.MyCouponResponse;
import com.twentyzhang.bluewhale.entity.Coupon;
import com.twentyzhang.bluewhale.entity.CouponGroup;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.CouponGroupMapper;
import com.twentyzhang.bluewhale.mapper.CouponMapper;
import com.twentyzhang.bluewhale.service.impl.CouponServiceImpl;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.RedisLockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CouponService")
class CouponServiceTest extends BaseServiceTest {

    @Mock private CouponMapper      couponMapper;
    @Mock private CouponGroupMapper couponGroupMapper;
    @Mock private RedisLockUtil     redisLockUtil;

    @InjectMocks
    private CouponServiceImpl couponService;

    /** 让分布式锁获取成功（领取流程能进入临界区）。 */
    private void lockAcquired() {
        when(redisLockUtil.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
    }

    // disambiguation helper
    private static Coupon anyCoupon() { return ArgumentMatchers.any(Coupon.class); }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(couponService, "baseMapper", couponMapper);
    }

    // ── 构建辅助 ──────────────────────────────────────────────────────────────

    private static CouponGroup group(Long id, int remainCount, LocalDateTime expireAt) {
        return CouponGroup.builder()
                .id(id).name("测试券").type("DISCOUNT")
                .value(new BigDecimal("0.5")).minOrderAmount(new BigDecimal("20.00"))
                .remainCount(remainCount).expireAt(expireAt).storeId(null)
                .build();
    }

    private static Coupon coupon(Long id, Long groupId, Long userId, String status) {
        return Coupon.builder().id(id).groupId(groupId).userId(userId).status(status).build();
    }

    // ── claimCoupon ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("claimCoupon")
    class ClaimCouponTests {

        @Test
        @DisplayName("正常领取，验证 remainCount 被减 1、coupon 记录被创建")
        void success_remainDecrementedAndCouponCreated() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            lockAcquired();
            when(couponGroupMapper.selectById(10L)).thenReturn(
                    group(10L, 5, LocalDateTime.now().plusDays(30)));
            when(couponMapper.selectCount(any())).thenReturn(0L); // 未重复领取
            when(couponGroupMapper.update(isNull(), any())).thenReturn(1);
            doAnswer(inv -> { ((Coupon) inv.getArgument(0)).setId(501L); return 1; })
                    .when(couponMapper).insert(anyCoupon());

            ClaimCouponResponse resp = couponService.claimCoupon(1L, 10L);

            assertEquals(501L, resp.getCouponId());
            // remainCount 减 1 通过 update(null, wrapper) 完成
            verify(couponGroupMapper).update(isNull(), any());
            // coupon 记录被创建
            ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
            verify(couponMapper).insert((Coupon) captor.capture());
            assertEquals(10L,     captor.getValue().getGroupId());
            assertEquals(1L,      captor.getValue().getUserId());
            assertEquals("UNUSED", captor.getValue().getStatus());
            // finally 中释放了锁
            verify(redisLockUtil).unlock(anyString(), anyString());
        }

        @Test
        @DisplayName("优惠券组不存在时抛出 BusinessException（code 404）")
        void groupNotFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            lockAcquired();
            when(couponGroupMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponService.claimCoupon(1L, 999L));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verifyNoInteractions(couponMapper);
        }

        @Test
        @DisplayName("优惠券组已过期时抛出 BusinessException")
        void expired_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            lockAcquired();
            when(couponGroupMapper.selectById(10L)).thenReturn(
                    group(10L, 5, LocalDateTime.now().minusDays(1))); // 过期

            assertThrows(BusinessException.class, () -> couponService.claimCoupon(1L, 10L));
            verifyNoInteractions(couponMapper);
        }

        @Test
        @DisplayName("remainCount 为 0 时抛出 BusinessException（提示已被抢完）")
        void noRemain_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            lockAcquired();
            when(couponGroupMapper.selectById(10L)).thenReturn(
                    group(10L, 0, LocalDateTime.now().plusDays(30))); // remainCount=0

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponService.claimCoupon(1L, 10L));
            assertTrue(ex.getMessage().contains("抢完"));
            verifyNoInteractions(couponMapper);
        }

        @Test
        @DisplayName("同一用户重复领取时抛出 BusinessException")
        void duplicateClaim_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            lockAcquired();
            when(couponGroupMapper.selectById(10L)).thenReturn(
                    group(10L, 5, LocalDateTime.now().plusDays(30)));
            when(couponMapper.selectCount(any())).thenReturn(1L); // 已领取过

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponService.claimCoupon(1L, 10L));
            assertTrue(ex.getMessage().contains("已领取"));
            verify(couponGroupMapper, never()).update(isNull(), any());
        }

        @Test
        @DisplayName("非 Customer 调用时抛出 BusinessException（code 403）")
        void notCustomer_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 100L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponService.claimCoupon(1L, 10L));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            // 鉴权先于加锁，连锁都不会尝试
            verifyNoInteractions(redisLockUtil, couponGroupMapper, couponMapper);
        }

        @Test
        @DisplayName("获取分布式锁失败时抛出『系统繁忙，请稍后重试』，不进入临界区、不释放锁")
        void lockAcquireFailed_throwsBusy() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(redisLockUtil.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponService.claimCoupon(1L, 10L));
            assertTrue(ex.getMessage().contains("系统繁忙"));
            // 没拿到锁：不查库、不解锁（避免误删他人持有的锁）
            verifyNoInteractions(couponGroupMapper, couponMapper);
            verify(redisLockUtil, never()).unlock(anyString(), anyString());
        }

        @Test
        @DisplayName("扣减时 remain_count>0 守卫命中（影响 0 行）抛出已被抢完，并释放锁")
        void decrementGuardZeroRows_throwsSoldOut() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            lockAcquired();
            when(couponGroupMapper.selectById(10L)).thenReturn(
                    group(10L, 5, LocalDateTime.now().plusDays(30)));
            when(couponMapper.selectCount(any())).thenReturn(0L);
            when(couponGroupMapper.update(isNull(), any())).thenReturn(0); // 守卫命中：库存已被并发领完

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponService.claimCoupon(1L, 10L));
            assertTrue(ex.getMessage().contains("抢完"));
            verify(couponMapper, never()).insert(anyCoupon()); // 未创建券记录
            verify(redisLockUtil).unlock(anyString(), anyString()); // finally 仍释放锁
        }
    }

    // ── getMyCoupons ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyCoupons")
    class GetMyCouponsTests {

        @Test
        @DisplayName("正常返回全部优惠券列表（不过滤）")
        void success_returnsAll() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(couponMapper.selectList(any())).thenReturn(List.of(
                    coupon(501L, 10L, 1L, "UNUSED"),
                    coupon(502L, 10L, 1L, "USED")
            ));
            CouponGroup g = group(10L, 30, LocalDateTime.now().plusDays(30));
            when(couponGroupMapper.selectBatchIds(any())).thenReturn(List.of(g));

            List<MyCouponResponse> result = couponService.getMyCoupons(1L, null);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("按 UNUSED 筛选时只返回未使用且未过期的优惠券")
        void filterUnused_excludesExpired() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            // coupon 501: group 未过期 → effectiveStatus=UNUSED
            // coupon 502: group 已过期 → effectiveStatus=EXPIRED（被过滤掉）
            when(couponMapper.selectList(any())).thenReturn(List.of(
                    coupon(501L, 10L, 1L, "UNUSED"),
                    coupon(502L, 20L, 1L, "UNUSED")
            ));
            when(couponGroupMapper.selectBatchIds(any())).thenReturn(List.of(
                    group(10L, 30, LocalDateTime.now().plusDays(30)), // 未过期
                    group(20L, 30, LocalDateTime.now().minusDays(1))  // 已过期
            ));

            List<MyCouponResponse> result = couponService.getMyCoupons(1L, "UNUSED");

            assertEquals(1, result.size());
            assertEquals(501L,    result.get(0).getId());
            assertEquals("UNUSED", result.get(0).getStatus());
        }

        @Test
        @DisplayName("DB 状态为 UNUSED 但已过期的优惠券，status 应返回 EXPIRED")
        void dbUnusedButExpired_statusIsExpired() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(couponMapper.selectList(any())).thenReturn(List.of(
                    coupon(501L, 10L, 1L, "UNUSED") // DB 中是 UNUSED
            ));
            when(couponGroupMapper.selectBatchIds(any())).thenReturn(List.of(
                    group(10L, 30, LocalDateTime.now().minusDays(1)) // 但 group 已过期
            ));

            List<MyCouponResponse> result = couponService.getMyCoupons(1L, "EXPIRED");

            assertEquals(1, result.size());
            assertEquals("EXPIRED", result.get(0).getStatus()); // Java 层判断为 EXPIRED
        }

        @Test
        @DisplayName("非 Customer 调用时抛出 BusinessException（code 403）")
        void notCustomer_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 100L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponService.getMyCoupons(1L, null));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verifyNoInteractions(couponMapper, couponGroupMapper);
        }
    }
}
