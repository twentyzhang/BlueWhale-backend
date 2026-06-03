package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CreateAddressRequest;
import com.twentyzhang.bluewhale.dto.UpdateAddressRequest;
import com.twentyzhang.bluewhale.dto.UserAddressResponse;
import com.twentyzhang.bluewhale.entity.UserAddress;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.UserAddressMapper;
import com.twentyzhang.bluewhale.service.impl.AddressServiceImpl;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AddressService")
class AddressServiceTest extends BaseServiceTest {

    @Mock private UserAddressMapper userAddressMapper;

    @InjectMocks
    private AddressServiceImpl addressService;

    // disambiguation helper（BaseMapper 3.5.9 insert/updateById 重载）
    private static UserAddress anyAddr() { return ArgumentMatchers.any(UserAddress.class); }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(addressService, "baseMapper", userAddressMapper);
    }

    // ── 构建辅助 ──────────────────────────────────────────────────────────────

    private static UserAddress addr(Long id, Long userId, int isDefault) {
        return UserAddress.builder()
                .id(id).userId(userId)
                .receiverName("张三").phone("13800138000")
                .province("江苏省").city("南京市").district("鼓楼区")
                .detail("汉口路" + id + "号")
                .isDefault(isDefault)
                .build();
    }

    private static CreateAddressRequest createReq(boolean isDefault) {
        CreateAddressRequest r = new CreateAddressRequest();
        r.setReceiverName("张三");
        r.setPhone("13800138000");
        r.setProvince("江苏省");
        r.setCity("南京市");
        r.setDistrict("鼓楼区");
        r.setDetail("汉口路22号");
        r.setIsDefault(isDefault);
        return r;
    }

    private static UpdateAddressRequest updateReq(String receiverName, Boolean isDefault) {
        UpdateAddressRequest r = new UpdateAddressRequest();
        r.setReceiverName(receiverName);
        r.setIsDefault(isDefault);
        return r;
    }

    // ── listAddresses ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listAddresses")
    class ListAddressTests {

        @Test
        @DisplayName("正常返回地址列表，默认地址排在第一位")
        void success_defaultFirst() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            // mock 返回已排好序的列表（排序由 SQL ORDER BY 保证，此处验证 VO 映射和排序条件）
            when(userAddressMapper.selectList(any())).thenReturn(List.of(
                    addr(1L, 1L, 1),   // 默认
                    addr(2L, 1L, 0)    // 非默认
            ));

            List<UserAddressResponse> result = addressService.getAddresses(1L);

            assertEquals(2, result.size());
            assertTrue(result.get(0).getIsDefault());   // 默认地址排第一
            assertFalse(result.get(1).getIsDefault());

            // 验证 wrapper 携带了 is_default 的 ORDER BY 条件
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<UserAddress>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(userAddressMapper).selectList(captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertNotNull(sql);
            assertTrue(sql.toLowerCase().contains("is_default"),
                    "ORDER BY 中应包含 is_default，实际 SQL: " + sql);
        }

        @Test
        @DisplayName("无地址时返回空列表")
        void noAddresses_returnsEmptyList() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectList(any())).thenReturn(List.of());

            List<UserAddressResponse> result = addressService.getAddresses(1L);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("非 Customer 调用时抛出 code=403 的 BusinessException")
        void notCustomer_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> addressService.getAddresses(1L));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verifyNoInteractions(userAddressMapper);
        }
    }

    // ── createAddress ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createAddress")
    class CreateAddressTests {

        @Test
        @DisplayName("正常新增普通地址（isDefault=false，不清除其他默认）")
        void success_normalAddress() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectCount(any())).thenReturn(2L);
            doAnswer(inv -> { ((UserAddress) inv.getArgument(0)).setId(10L); return 1; })
                    .when(userAddressMapper).insert(anyAddr());

            Long id = addressService.addAddress(1L, createReq(false));

            assertEquals(10L, id);
            // 非默认地址，不调用 update 清除旧默认
            verify(userAddressMapper, never()).update(isNull(), any());
            // 插入的地址 isDefault 为 0
            ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
            verify(userAddressMapper).insert((UserAddress) captor.capture());
            assertEquals(0, captor.getValue().getIsDefault());
        }

        @Test
        @DisplayName("isDefault=true 时验证其他地址被取消默认")
        void isDefaultTrue_clearsOthers() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectCount(any())).thenReturn(2L);
            when(userAddressMapper.update(isNull(), any())).thenReturn(1);
            doAnswer(inv -> { ((UserAddress) inv.getArgument(0)).setId(11L); return 1; })
                    .when(userAddressMapper).insert(anyAddr());

            Long id = addressService.addAddress(1L, createReq(true));

            assertEquals(11L, id);
            // 应调用一次 update 清除其他默认
            verify(userAddressMapper).update(isNull(), any());
            // 插入的地址 isDefault 为 1
            ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
            verify(userAddressMapper).insert((UserAddress) captor.capture());
            assertEquals(1, captor.getValue().getIsDefault());
        }

        @Test
        @DisplayName("用户首条地址时无论 isDefault 传何值都自动设为默认")
        void firstAddress_alwaysDefault() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectCount(any())).thenReturn(0L); // 无已有地址
            doAnswer(inv -> { ((UserAddress) inv.getArgument(0)).setId(12L); return 1; })
                    .when(userAddressMapper).insert(anyAddr());

            // 显式传 isDefault=false，仍应被强制设为默认
            Long id = addressService.addAddress(1L, createReq(false));

            assertEquals(12L, id);
            // 首条地址无旧默认可清，不应调用 update
            verify(userAddressMapper, never()).update(isNull(), any());
            // 插入的地址 isDefault 被强制置为 1
            ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
            verify(userAddressMapper).insert((UserAddress) captor.capture());
            assertEquals(1, captor.getValue().getIsDefault());
        }
    }

    // ── updateAddress ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateAddress")
    class UpdateAddressTests {

        @Test
        @DisplayName("正常更新地址字段（未改 isDefault）")
        void success_updatesFields() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectById(1L)).thenReturn(addr(1L, 1L, 0));
            when(userAddressMapper.updateById(anyAddr())).thenReturn(1);

            addressService.updateAddress(1L, 1L, updateReq("李四", null));

            // 验证更新后的姓名
            ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
            verify(userAddressMapper).updateById((UserAddress) captor.capture());
            assertEquals("李四", captor.getValue().getReceiverName());
            // isDefault 未传，不清除其他地址
            verify(userAddressMapper, never()).update(isNull(), any());
        }

        @Test
        @DisplayName("设为默认地址时验证其他地址被取消默认")
        void setDefault_clearsOthers() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectById(1L)).thenReturn(addr(1L, 1L, 0));
            when(userAddressMapper.updateById(anyAddr())).thenReturn(1);
            when(userAddressMapper.update(isNull(), any())).thenReturn(1);

            addressService.updateAddress(1L, 1L, updateReq(null, true));

            // updateById 将自身 isDefault 设为 1
            ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
            verify(userAddressMapper).updateById((UserAddress) captor.capture());
            assertEquals(1, captor.getValue().getIsDefault());
            // 清除其他地址的默认标记
            verify(userAddressMapper).update(isNull(), any());
        }

        @Test
        @DisplayName("地址不存在时抛出 code=404 的 BusinessException")
        void notFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> addressService.updateAddress(1L, 999L, updateReq("李四", null)));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verify(userAddressMapper, never()).updateById(anyAddr());
        }

        @Test
        @DisplayName("操作其他用户地址时抛出 code=403 的 BusinessException")
        void otherUserAddress_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            // 地址属于 userId=2，当前用户是 1
            when(userAddressMapper.selectById(1L)).thenReturn(addr(1L, 2L, 0));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> addressService.updateAddress(1L, 1L, updateReq("李四", null)));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verify(userAddressMapper, never()).updateById(anyAddr());
        }
    }

    // ── deleteAddress ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteAddress")
    class DeleteAddressTests {

        @Test
        @DisplayName("正常删除非默认地址")
        void success_nonDefault() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectById(1L)).thenReturn(addr(1L, 1L, 0)); // 非默认

            addressService.deleteAddress(1L, 1L);

            verify(userAddressMapper).deleteById((Serializable) 1L);
            // 非默认地址，不查剩余、不设新默认
            verify(userAddressMapper, never()).selectList(any());
            verify(userAddressMapper, never()).updateById(anyAddr());
        }

        @Test
        @DisplayName("删除默认地址时最新一条地址被自动设为新默认")
        void deleteDefault_promotesNewest() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectById(1L)).thenReturn(addr(1L, 1L, 1)); // 默认地址
            // 删除后剩余：addr(2L) 是最新的
            when(userAddressMapper.selectList(any())).thenReturn(List.of(addr(2L, 1L, 0)));
            when(userAddressMapper.updateById(anyAddr())).thenReturn(1);

            addressService.deleteAddress(1L, 1L);

            verify(userAddressMapper).deleteById((Serializable) 1L);
            ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
            verify(userAddressMapper).updateById((UserAddress) captor.capture());
            assertEquals(2L,  captor.getValue().getId());
            assertEquals(1,   captor.getValue().getIsDefault()); // 升为默认
        }

        @Test
        @DisplayName("删除默认地址后无剩余地址时不抛出异常，且不更新任何地址")
        void deleteDefault_noRemaining_noUpdate() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectById(1L)).thenReturn(addr(1L, 1L, 1)); // 默认地址
            when(userAddressMapper.selectList(any())).thenReturn(List.of()); // 删后无剩余

            assertDoesNotThrow(() -> addressService.deleteAddress(1L, 1L));

            verify(userAddressMapper).deleteById((Serializable) 1L);
            verify(userAddressMapper, never()).updateById(anyAddr());
        }

        @Test
        @DisplayName("地址不存在时抛出 code=404 的 BusinessException")
        void notFound_throws404() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> addressService.deleteAddress(1L, 999L));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verify(userAddressMapper, never()).deleteById((Serializable) any());
        }

        @Test
        @DisplayName("操作其他用户地址时抛出 code=403 的 BusinessException")
        void otherUserAddress_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
            when(userAddressMapper.selectById(1L)).thenReturn(addr(1L, 2L, 0)); // userId=2

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> addressService.deleteAddress(1L, 1L));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verify(userAddressMapper, never()).deleteById((Serializable) any());
        }
    }
}
