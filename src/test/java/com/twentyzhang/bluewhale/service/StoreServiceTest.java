package com.twentyzhang.bluewhale.service;

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
import com.twentyzhang.bluewhale.service.impl.StoreServiceImpl;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.CacheKeys;
import com.twentyzhang.bluewhale.util.CacheUtil;
import com.twentyzhang.bluewhale.util.RedisUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@DisplayName("StoreService")
class StoreServiceTest extends BaseServiceTest {

    @Mock
    private StoreMapper storeMapper;

    @Mock
    private UserMapper userMapper;

    // StoreServiceImpl 构造器参数，@InjectMocks 时必须声明；
    // listStores / listAllStoresForAdmin 内部调用 productMapper.selectCount()，
    // 测试中需要对其进行 stub。
    @Mock
    private ProductMapper productMapper;

    @Mock
    private CacheUtil cacheUtil;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private StoreServiceImpl storeService;

    /**
     * 返回类型显式为 Store，让编译器选择 insert(Store) 而非 insert(Collection<Store>)。
     * BaseMapper 3.5.9 新增了 insert(Collection<T>) 默认方法，导致 any() / (T)any() 歧义，
     * 私有工厂方法是最简洁的消歧手段。详见 docs/debug记录.md 第 1 条。
     */
    private static Store anyStore() {
        return ArgumentMatchers.any(Store.class);
    }

    @BeforeEach
    void setUp() {
        // baseMapper 泛型擦除导致 Mockito 字段注入失败，手动注入。
        // 详见 docs/debug记录.md 第 3 条。
        ReflectionTestUtils.setField(storeService, "baseMapper", storeMapper);
        // CacheUtil 透传：执行 loader 返回结果；单实体 loader 返回 null 时按 getOrLoad 语义抛 404
        lenient().when(cacheUtil.getOrLoad(anyString(), anyLong(), anyLong(),
                        any(Supplier.class), any(Class.class)))
                .thenAnswer(inv -> {
                    Object v = ((Supplier<?>) inv.getArgument(3)).get();
                    if (v == null) throw new BusinessException(Result.CODE_NOT_FOUND, "资源不存在");
                    return v;
                });
        lenient().when(cacheUtil.getOrLoad(anyString(), anyLong(), anyLong(),
                        any(Supplier.class), any(TypeReference.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listStores
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listStores")
    class ListStores {

        @Test
        @DisplayName("正常分页返回商店列表，每条记录包含 productCount，不含 creditCode")
        void success() {
            Store store = Store.builder()
                    .id(1L).name("南鲸旗舰店").logo("logo.png")
                    .creditCode("91320100XXXXX").build();

            Page<Store> page = new Page<>(1, 10);
            page.setRecords(List.of(store));
            page.setTotal(1L);

            when(storeMapper.selectPage(any(), any())).thenReturn(page);
            when(productMapper.selectCount(any())).thenReturn(8L);

            IPage<StoreListItemResponse> result = storeService.getStoreList(1, 10);

            assertEquals(1L, result.getTotal());
            StoreListItemResponse item = result.getRecords().get(0);
            assertEquals(1L,             item.getId());
            assertEquals("南鲸旗舰店",    item.getName());
            assertEquals("logo.png",     item.getLogo());
            assertEquals(8,              item.getProductCount());
            assertNull(item.getCreditCode()); // 公开接口不暴露 creditCode
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getStoreDetail
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStoreDetail")
    class GetStoreDetail {

        @Test
        @DisplayName("商店存在时返回完整详情（含 creditCode）")
        void success() {
            Store store = Store.builder()
                    .id(1L).name("南鲸旗舰店")
                    .creditCode("91320100XXXXX").logo("logo.png").build();

            when(storeMapper.selectById(1L)).thenReturn(store);

            StoreDetailResponse resp = storeService.getStoreById(1L);

            assertEquals(1L,              resp.getId());
            assertEquals("南鲸旗舰店",     resp.getName());
            assertEquals("91320100XXXXX", resp.getCreditCode());
            assertEquals("logo.png",      resp.getLogo());
        }

        @Test
        @DisplayName("商店不存在时抛出 code=404 的 BusinessException")
        void notFound() {
            when(storeMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storeService.getStoreById(999L));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            // 经 CacheUtil 穿透防护后，未找到统一抛通用 404 消息
            assertEquals("资源不存在", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createStore
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createStore")
    class CreateStore {

        @Test
        @DisplayName("Admin 正常创建商店，Staff 用户的 storeId 被更新为新商店 id")
        void success() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            CreateStoreRequest req = new CreateStoreRequest();
            req.setName("南鲸旗舰店");
            req.setCreditCode("91320100XXXXX");
            req.setLogo("logo.png");
            req.setStaffPhone("13900139000");

            User staff = User.builder().id(2L).phone("13900139000").role("STAFF").build();
            when(userMapper.selectByPhone("13900139000")).thenReturn(staff);

            // anyStore() 返回类型为 Store，消除 insert 重载歧义；
            // doAnswer 模拟 MyBatis Plus 自增 ID 回写。详见 docs/debug记录.md 第 1、3 条。
            doAnswer(inv -> {
                Store s = inv.getArgument(0);
                s.setId(10L);
                return 1;
            }).when(storeMapper).insert(anyStore());

            when(userMapper.updateById(any(User.class))).thenReturn(1);

            CreateStoreResponse resp = storeService.createStore(req);

            // 返回值包含新商店 id
            assertEquals(10L, resp.getStoreId());

            // Staff 的 storeId 被更新
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(captor.capture());
            assertEquals(10L, captor.getValue().getStoreId());
        }

        @Test
        @DisplayName("当前用户非 Admin 时抛出 code=403 的 BusinessException")
        void notAdmin() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storeService.createStore(new CreateStoreRequest()));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
        }

        @Test
        @DisplayName("staffPhone 对应用户不存在时抛出 BusinessException")
        void staffNotFound() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            CreateStoreRequest req = new CreateStoreRequest();
            req.setStaffPhone("13900139000");

            when(userMapper.selectByPhone("13900139000")).thenReturn(null);

            assertThrows(BusinessException.class, () -> storeService.createStore(req));
            // 异常在写入商店前抛出，storeMapper 应无任何交互
            verifyNoInteractions(storeMapper);
        }

        @Test
        @DisplayName("staffPhone 对应用户角色不是 STAFF 时抛出 BusinessException")
        void staffWrongRole() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            CreateStoreRequest req = new CreateStoreRequest();
            req.setStaffPhone("13900139000");

            User customer = User.builder().phone("13900139000").role("CUSTOMER").build();
            when(userMapper.selectByPhone("13900139000")).thenReturn(customer);

            assertThrows(BusinessException.class, () -> storeService.createStore(req));
            verifyNoInteractions(storeMapper);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateStore
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStore")
    class UpdateStore {

        @Test
        @DisplayName("Admin 正常更新商店，只修改请求体中传入的字段")
        void success() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            Store store = Store.builder()
                    .id(1L).name("旧名").logo("old.png").build();

            UpdateStoreRequest req = new UpdateStoreRequest();
            req.setName("新名");
            // logo 不传，不应被修改

            when(storeMapper.selectById(1L)).thenReturn(store);
            when(storeMapper.updateById(anyStore())).thenReturn(1);

            assertDoesNotThrow(() -> storeService.updateStore(1L, req));

            assertEquals("新名",   store.getName());
            assertEquals("old.png", store.getLogo()); // 未修改
            verify(storeMapper).updateById(store);
            // 更新后失效该商店详情 + 列表缓存
            verify(redisUtil).delete(CacheKeys.storeDetail(1L));
            verify(redisUtil).deleteByPrefix(CacheKeys.STORE_LIST_PREFIX);
        }

        @Test
        @DisplayName("Admin 更新商店：name 与 logo 均传入时全部更新")
        void allFieldsUpdated() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            Store store = Store.builder().id(1L).name("旧名").logo("old.png").build();
            UpdateStoreRequest req = new UpdateStoreRequest();
            req.setName("新名");
            req.setLogo("new.png");

            when(storeMapper.selectById(1L)).thenReturn(store);
            when(storeMapper.updateById(anyStore())).thenReturn(1);

            storeService.updateStore(1L, req);

            assertEquals("新名",  store.getName());
            assertEquals("new.png", store.getLogo());
        }

        @Test
        @DisplayName("商店不存在时抛出 code=404 的 BusinessException")
        void notFound() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            when(storeMapper.selectById(999L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storeService.updateStore(999L, new UpdateStoreRequest()));
            assertEquals(Result.CODE_NOT_FOUND, ex.getCode());
            verify(storeMapper, never()).updateById(anyStore());
        }

        @Test
        @DisplayName("当前用户非 Admin 时抛出 code=403 的 BusinessException")
        void notAdmin() {
            mockAuthUser(1L, AuthUtil.ROLE_STAFF, 1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storeService.updateStore(1L, new UpdateStoreRequest()));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            // 权限校验应在查库前失败
            verify(storeMapper, never()).selectById(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listAllStoresForAdmin
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listAllStoresForAdmin")
    class ListAllStoresForAdmin {

        @Test
        @DisplayName("Admin 分页返回商店列表，响应包含 creditCode 字段")
        void success() {
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

            assertEquals(1L, result.getTotal());
            StoreListItemResponse item = result.getRecords().get(0);
            assertEquals("91320100XXXXX", item.getCreditCode()); // Admin 接口填充 creditCode
            assertEquals(3,               item.getProductCount());
        }

        @Test
        @DisplayName("当前用户非 Admin 时抛出 code=403 的 BusinessException")
        void notAdmin() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> storeService.getAdminStoreList(1, 10));
            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verify(storeMapper, never()).selectPage(any(), any());
        }
    }
}
