package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.*;
import com.twentyzhang.bluewhale.entity.Store;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.mapper.StoreMapper;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.util.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@DisplayName("StoreServiceImpl 单元测试")
class StoreServiceImplTest extends BaseServiceTest {

    @Mock
    private StoreMapper storeMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private StoreServiceImpl storeService;

    private static User anyUser()  { return ArgumentMatchers.any(User.class);  }
    private static Store anyStore() { return ArgumentMatchers.any(Store.class); }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storeService, "baseMapper", storeMapper);
    }

    // ── getStoreList ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("商店列表：分页返回并统计每店商品数，不含 creditCode")
    void getStoreList_returnsPageWithProductCount() {
        Store store = Store.builder().id(1L).name("南鲸旗舰店").logo("logo.png").build();
        Page<Store> page = new Page<>(1, 10);
        page.setRecords(List.of(store));
        page.setTotal(1L);

        when(storeMapper.selectPage(any(), any())).thenReturn(page);
        when(productMapper.selectCount(any())).thenReturn(5L);

        IPage<StoreListItemResponse> result = storeService.getStoreList(1, 10);

        assertEquals(1L, result.getTotal());
        StoreListItemResponse item = result.getRecords().get(0);
        assertEquals("南鲸旗舰店", item.getName());
        assertEquals(5, item.getProductCount());
        assertNull(item.getCreditCode());
    }

    // ── getStoreById ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("商店详情：存在时返回含 creditCode 的详情")
    void getStoreById_success() {
        Store store = Store.builder()
                .id(1L).name("南鲸旗舰店")
                .creditCode("91320100XXXXX").logo("logo.png").build();

        when(storeMapper.selectById(1L)).thenReturn(store);

        StoreDetailResponse resp = storeService.getStoreById(1L);

        assertEquals(1L, resp.getId());
        assertEquals("91320100XXXXX", resp.getCreditCode());
        assertEquals("南鲸旗舰店", resp.getName());
    }

    @Test
    @DisplayName("商店详情：商店不存在时抛 404 BusinessException")
    void getStoreById_notFound() {
        when(storeMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.getStoreById(999L));
        assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
        assertEquals("商店不存在", ex.getMessage());
    }

    // ── createStore ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("创建商店：Admin 操作成功，Staff 的 storeId 被更新")
    void createStore_success() {
        mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

        CreateStoreRequest req = new CreateStoreRequest();
        req.setName("南鲸旗舰店");
        req.setCreditCode("91320100XXXXX");
        req.setLogo("logo.png");
        req.setStaffPhone("13900139000");

        User staff = User.builder().id(2L).phone("13900139000").role("STAFF").build();
        when(userMapper.selectByPhone("13900139000")).thenReturn(staff);

        // 模拟 MyBatis Plus 自增 ID 回写
        doAnswer(inv -> {
            Store s = inv.getArgument(0);
            s.setId(10L);
            return 1;
        }).when(storeMapper).insert(anyStore());

        when(userMapper.updateById(anyUser())).thenReturn(1);

        CreateStoreResponse resp = storeService.createStore(req);

        assertEquals(10L, resp.getStoreId());
        assertEquals(10L, staff.getStoreId());
    }

    @Test
    @DisplayName("创建商店：staffPhone 对应用户不存在时抛 BusinessException")
    void createStore_staffNotFound() {
        mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

        CreateStoreRequest req = new CreateStoreRequest();
        req.setStaffPhone("13900139000");

        when(userMapper.selectByPhone("13900139000")).thenReturn(null);

        assertThrows(BusinessException.class, () -> storeService.createStore(req));
    }

    @Test
    @DisplayName("创建商店：staffPhone 对应用户角色不是 STAFF 时抛 BusinessException")
    void createStore_staffWrongRole() {
        mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

        CreateStoreRequest req = new CreateStoreRequest();
        req.setStaffPhone("13900139000");

        User customer = User.builder().phone("13900139000").role("CUSTOMER").build();
        when(userMapper.selectByPhone("13900139000")).thenReturn(customer);

        assertThrows(BusinessException.class, () -> storeService.createStore(req));
    }

    @Test
    @DisplayName("创建商店：非 Admin 调用时抛 403 BusinessException")
    void createStore_notAdmin() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.createStore(new CreateStoreRequest()));
        assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
    }

    // ── updateStore ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("修改商店：只更新传入字段，未传字段保持原值")
    void updateStore_partialUpdate() {
        mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

        Store store = Store.builder().id(1L).name("旧名").logo("old_logo.png").build();
        UpdateStoreRequest req = new UpdateStoreRequest();
        req.setName("新名");
        // logo 不传

        when(storeMapper.selectById(1L)).thenReturn(store);
        when(storeMapper.updateById(anyStore())).thenReturn(1);

        storeService.updateStore(1L, req);

        assertEquals("新名", store.getName());
        assertEquals("old_logo.png", store.getLogo()); // 未修改
    }

    @Test
    @DisplayName("修改商店：两个字段均传入时全部更新")
    void updateStore_allFieldsUpdated() {
        mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

        Store store = Store.builder().id(1L).name("旧名").logo("old_logo.png").build();
        UpdateStoreRequest req = new UpdateStoreRequest();
        req.setName("新名");
        req.setLogo("new_logo.png");

        when(storeMapper.selectById(1L)).thenReturn(store);
        when(storeMapper.updateById(anyStore())).thenReturn(1);

        storeService.updateStore(1L, req);

        assertEquals("新名", store.getName());
        assertEquals("new_logo.png", store.getLogo());
    }

    @Test
    @DisplayName("修改商店：商店不存在时抛 404 BusinessException")
    void updateStore_notFound() {
        mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

        when(storeMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.updateStore(999L, new UpdateStoreRequest()));
        assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("修改商店：非 Admin 调用时抛 403 BusinessException")
    void updateStore_notAdmin() {
        mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.updateStore(1L, new UpdateStoreRequest()));
        assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
    }

    // ── getAdminStoreList ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Admin 商店列表：响应中包含 creditCode")
    void getAdminStoreList_includesCreditCode() {
        mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

        Store store = Store.builder()
                .id(1L).name("南鲸旗舰店")
                .creditCode("91320100XXXXX").logo("logo.png").build();
        Page<Store> page = new Page<>(1, 10);
        page.setRecords(List.of(store));
        page.setTotal(1L);

        when(storeMapper.selectPage(any(), any())).thenReturn(page);
        when(productMapper.selectCount(any())).thenReturn(3L);

        IPage<StoreListItemResponse> result = storeService.getAdminStoreList(1, 10);

        StoreListItemResponse item = result.getRecords().get(0);
        assertEquals("91320100XXXXX", item.getCreditCode());
        assertEquals(3, item.getProductCount());
    }

    @Test
    @DisplayName("Admin 商店列表：非 Admin 调用时抛 403 BusinessException")
    void getAdminStoreList_notAdmin() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storeService.getAdminStoreList(1, 10));
        assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
    }
}
