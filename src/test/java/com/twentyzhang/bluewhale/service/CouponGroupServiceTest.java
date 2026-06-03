package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CouponGroupResponse;
import com.twentyzhang.bluewhale.dto.CreateCouponGroupRequest;
import com.twentyzhang.bluewhale.entity.CouponGroup;
import com.twentyzhang.bluewhale.entity.Store;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.CouponGroupMapper;
import com.twentyzhang.bluewhale.mapper.CouponMapper;
import com.twentyzhang.bluewhale.mapper.StoreMapper;
import com.twentyzhang.bluewhale.service.impl.CouponGroupServiceImpl;
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

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CouponGroupService")
class CouponGroupServiceTest extends BaseServiceTest {

    @Mock private CouponGroupMapper couponGroupMapper;
    @Mock private StoreMapper        storeMapper;
    @Mock private CouponMapper       couponMapper;

    @InjectMocks
    private CouponGroupServiceImpl couponGroupService;

    // disambiguation helper（BaseMapper 3.5.9 insert/updateById 重载）
    private static CouponGroup anyCouponGroup() { return ArgumentMatchers.any(CouponGroup.class); }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(couponGroupService, "baseMapper", couponGroupMapper);
    }

    // ── 构建辅助 ──────────────────────────────────────────────────────────────

    private static CouponGroup group(Long id, Long storeId, int remainCount,
                                      LocalDateTime expireAt) {
        return CouponGroup.builder()
                .id(id).storeId(storeId).name("测试券").type("DISCOUNT")
                .value(new BigDecimal("0.8")).minOrderAmount(BigDecimal.ZERO)
                .totalCount(100).remainCount(remainCount)
                .startAt(LocalDateTime.now().minusDays(1))
                .expireAt(expireAt).build();
    }

    private static Page<CouponGroup> pageOf(CouponGroup... groups) {
        Page<CouponGroup> p = new Page<>(1, 10);
        p.setRecords(List.of(groups));
        p.setTotal(groups.length);
        return p;
    }

    private static CreateCouponGroupRequest discountReq(BigDecimal value) {
        CreateCouponGroupRequest r = new CreateCouponGroupRequest();
        r.setName("折扣券");
        r.setType("DISCOUNT");
        r.setValue(value);
        r.setMinOrderAmount(BigDecimal.ZERO);
        r.setTotalCount(50);
        r.setStartAt(LocalDateTime.now().minusDays(1));
        r.setExpireAt(LocalDateTime.now().plusDays(30));
        return r;
    }

    private static CreateCouponGroupRequest amountOffReq(BigDecimal value) {
        CreateCouponGroupRequest r = new CreateCouponGroupRequest();
        r.setName("满减券");
        r.setType("AMOUNT_OFF");
        r.setValue(value);
        r.setMinOrderAmount(new BigDecimal("50.00"));
        r.setTotalCount(50);
        r.setStartAt(LocalDateTime.now().minusDays(1));
        r.setExpireAt(LocalDateTime.now().plusDays(30));
        return r;
    }

    // ── listCouponGroups ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("listCouponGroups")
    class ListCouponGroupsTests {

        @Test
        @DisplayName("正常返回有效优惠券组列表，storeName 正确填充")
        void success_returnsValidGroupsWithStoreName() {
            CouponGroup g = group(10L, 100L, 63, LocalDateTime.now().plusDays(30));
            when(couponGroupMapper.selectPage(any(), any())).thenReturn(pageOf(g));
            when(storeMapper.selectBatchIds(any())).thenReturn(
                    List.of(Store.builder().id(100L).name("南鲸旗舰店").build()));

            IPage<CouponGroupResponse> result = couponGroupService.getAvailableCouponGroups(1, 10);

            assertEquals(1, result.getRecords().size());
            CouponGroupResponse resp = result.getRecords().get(0);
            assertEquals(10L, resp.getId());
            assertEquals(63, resp.getRemainCount());
            assertEquals("南鲸旗舰店", resp.getStoreName());
        }

        @Test
        @DisplayName("已过期的优惠券组不在返回列表中（验证 wrapper 包含 expire_at 条件）")
        void expiredGroups_wrapperContainsExpireAtCondition() {
            when(couponGroupMapper.selectPage(any(), any())).thenReturn(pageOf()); // DB 已过滤

            couponGroupService.getAvailableCouponGroups(1, 10);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<CouponGroup>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(couponGroupMapper).selectPage(any(), captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertNotNull(sql);
            assertTrue(sql.contains("expire_at"),
                    "WHERE 条件中应包含 expire_at，实际 SQL: " + sql);
        }

        @Test
        @DisplayName("remainCount 为 0 的优惠券组不在返回列表中（验证 wrapper 包含 remain_count 条件）")
        void zeroRemainCount_wrapperContainsRemainCountCondition() {
            when(couponGroupMapper.selectPage(any(), any())).thenReturn(pageOf()); // DB 已过滤

            couponGroupService.getAvailableCouponGroups(1, 10);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<CouponGroup>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(couponGroupMapper).selectPage(any(), captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertNotNull(sql);
            assertTrue(sql.contains("remain_count"),
                    "WHERE 条件中应包含 remain_count，实际 SQL: " + sql);
        }
    }

    // ── createStoreCouponGroup ────────────────────────────────────────────────

    @Nested
    @DisplayName("createStoreCouponGroup")
    class CreateStoreCouponGroupTests {

        @Test
        @DisplayName("正常创建店铺优惠券组，验证 storeId 和 remainCount 初始值")
        void success_storeIdAndRemainCountCorrect() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 100L);
            doAnswer(inv -> { ((CouponGroup) inv.getArgument(0)).setId(10L); return 1; })
                    .when(couponGroupMapper).insert(anyCouponGroup());

            Long id = couponGroupService.createStoreCouponGroup(100L, discountReq(new BigDecimal("0.5")));

            assertEquals(10L, id);
            ArgumentCaptor<CouponGroup> captor = ArgumentCaptor.forClass(CouponGroup.class);
            verify(couponGroupMapper).insert((CouponGroup) captor.capture());
            assertEquals(100L, captor.getValue().getStoreId());
            assertEquals(50, captor.getValue().getRemainCount()); // remainCount = totalCount = 50
        }

        @Test
        @DisplayName("startAt 晚于 expireAt 时抛出 BusinessException")
        void startAfterExpire_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 100L);
            CreateCouponGroupRequest req = discountReq(new BigDecimal("0.5"));
            req.setStartAt(LocalDateTime.now().plusDays(10));
            req.setExpireAt(LocalDateTime.now().plusDays(5)); // expire 早于 start

            assertThrows(BusinessException.class,
                    () -> couponGroupService.createStoreCouponGroup(100L, req));
            verify(couponGroupMapper, never()).insert(anyCouponGroup());
        }

        @Test
        @DisplayName("DISCOUNT 类型 value 为 1（不含）时抛出 BusinessException")
        void discountValueBoundary_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 100L);

            // value=1 不合法（必须 < 1）
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponGroupService.createStoreCouponGroup(100L, discountReq(BigDecimal.ONE)));
            assertTrue(ex.getMessage().contains("DISCOUNT"));
        }

        @Test
        @DisplayName("AMOUNT_OFF 类型 value 小于等于 0 时抛出 BusinessException")
        void amountOffZeroValue_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 100L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponGroupService.createStoreCouponGroup(100L, amountOffReq(BigDecimal.ZERO)));
            assertTrue(ex.getMessage().contains("AMOUNT_OFF"));
        }

        @Test
        @DisplayName("非 Staff 调用时抛出 BusinessException（code 403）")
        void notStaff_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponGroupService.createStoreCouponGroup(100L, discountReq(new BigDecimal("0.5"))));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verifyNoInteractions(couponGroupMapper);
        }

        @Test
        @DisplayName("Staff 操作其他店铺时抛出 BusinessException（code 403）")
        void staffWrongStore_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 200L); // Staff 属于 store 200，操作 store 100

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponGroupService.createStoreCouponGroup(100L, discountReq(new BigDecimal("0.5"))));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
        }
    }

    // ── createGlobalCouponGroup ───────────────────────────────────────────────

    @Nested
    @DisplayName("createGlobalCouponGroup")
    class CreateGlobalCouponGroupTests {

        @Test
        @DisplayName("正常创建全局优惠券组，验证 storeId 为 null")
        void success_storeIdIsNull() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);
            doAnswer(inv -> { ((CouponGroup) inv.getArgument(0)).setId(20L); return 1; })
                    .when(couponGroupMapper).insert(anyCouponGroup());

            Long id = couponGroupService.createGlobalCouponGroup(amountOffReq(new BigDecimal("10.00")));

            assertEquals(20L, id);
            ArgumentCaptor<CouponGroup> captor = ArgumentCaptor.forClass(CouponGroup.class);
            verify(couponGroupMapper).insert((CouponGroup) captor.capture());
            assertNull(captor.getValue().getStoreId()); // 全局券 storeId 必须为 null
        }

        @Test
        @DisplayName("非 Admin 调用时抛出 BusinessException（code 403）")
        void notAdmin_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 100L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponGroupService.createGlobalCouponGroup(amountOffReq(new BigDecimal("10.00"))));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verifyNoInteractions(couponGroupMapper);
        }
    }

    // ── deleteCouponGroup ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteCouponGroup")
    class DeleteCouponGroupTests {

        @Test
        @DisplayName("Admin 正常删除任意优惠券组（全局券）")
        void admin_deleteGlobalGroup_success() {
            when(couponGroupMapper.selectById(10L)).thenReturn(group(10L, null, 50, LocalDateTime.now().plusDays(30)));
            when(couponMapper.selectCount(any())).thenReturn(0L); // 无人领取
            when(couponGroupMapper.deleteById((Serializable) 10L)).thenReturn(1);

            couponGroupService.deleteCouponGroup(null, AuthUtil.ROLE_ADMIN, 10L);

            verify(couponGroupMapper).deleteById((Serializable) 10L);
        }

        @Test
        @DisplayName("Staff 正常删除本店优惠券组")
        void staff_deleteOwnStoreGroup_success() {
            when(couponGroupMapper.selectById(10L)).thenReturn(group(10L, 100L, 50, LocalDateTime.now().plusDays(30)));
            when(couponMapper.selectCount(any())).thenReturn(0L);
            when(couponGroupMapper.deleteById((Serializable) 10L)).thenReturn(1);

            couponGroupService.deleteCouponGroup(100L, AuthUtil.ROLE_STAFF, 10L);

            verify(couponGroupMapper).deleteById((Serializable) 10L);
        }

        @Test
        @DisplayName("Staff 删除全局优惠券时抛出 BusinessException（code 403）")
        void staff_deleteGlobalGroup_throws403() {
            when(couponGroupMapper.selectById(10L)).thenReturn(group(10L, null, 50, LocalDateTime.now().plusDays(30)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponGroupService.deleteCouponGroup(100L, AuthUtil.ROLE_STAFF, 10L));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verify(couponGroupMapper, never()).deleteById((Serializable) any());
        }

        @Test
        @DisplayName("已有用户领取时抛出 BusinessException")
        void hasClaimed_throws() {
            when(couponGroupMapper.selectById(10L)).thenReturn(group(10L, 100L, 50, LocalDateTime.now().plusDays(30)));
            when(couponMapper.selectCount(any())).thenReturn(3L); // 3 人已领取

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponGroupService.deleteCouponGroup(100L, AuthUtil.ROLE_STAFF, 10L));
            assertTrue(ex.getMessage().contains("3")); // 消息包含已领取数量
            verify(couponGroupMapper, never()).deleteById((Serializable) any());
        }

        @Test
        @DisplayName("优惠券组不存在时抛出 BusinessException（code 404）")
        void groupNotFound_throws404() {
            when(couponGroupMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> couponGroupService.deleteCouponGroup(null, AuthUtil.ROLE_ADMIN, 999L));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
        }
    }
}
