package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CreateStoreRequest;
import com.twentyzhang.bluewhale.dto.CreateStoreResponse;
import com.twentyzhang.bluewhale.dto.StoreDetailResponse;
import com.twentyzhang.bluewhale.dto.StoreListItemResponse;
import com.twentyzhang.bluewhale.dto.UpdateStoreRequest;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.Store;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.mapper.StoreMapper;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.service.StoreService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.CacheKeys;
import com.twentyzhang.bluewhale.util.CacheUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl extends ServiceImpl<StoreMapper, Store> implements StoreService {

    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final CacheUtil cacheUtil;

    // ── 1. listStores ────────────────────────────────────────────────────────
    @Override
    @SuppressWarnings("unchecked")
    public IPage<StoreListItemResponse> getStoreList(int page, int size) {
        return cacheUtil.getOrLoad(CacheKeys.storeList(page, size),
                CacheKeys.STORE_LIST_TTL, CacheKeys.STORE_LIST_JITTER,
                () -> (Page<StoreListItemResponse>)
                        this.page(new Page<Store>(page, size)).convert(s -> toListItem(s, false)),
                new TypeReference<Page<StoreListItemResponse>>() {});
    }

    // ── 2. getStoreDetail ────────────────────────────────────────────────────
    @Override
    public StoreDetailResponse getStoreById(Long storeId) {
        return cacheUtil.getOrLoad(CacheKeys.storeDetail(storeId),
                CacheKeys.STORE_DETAIL_TTL, CacheKeys.STORE_DETAIL_JITTER,
                () -> {
                    Store store = getById(storeId);
                    if (store == null) {
                        return null; // → CacheUtil 写空值占位并抛 404
                    }
                    return StoreDetailResponse.builder()
                            .id(store.getId())
                            .name(store.getName())
                            .creditCode(store.getCreditCode())
                            .logo(store.getLogo())
                            .build();
                },
                StoreDetailResponse.class);
    }

    // ── 3. createStore ───────────────────────────────────────────────────────
    @Override
    @Transactional
    public CreateStoreResponse createStore(CreateStoreRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_ADMIN);

        User staff = userMapper.selectByPhone(request.getStaffPhone());
        if (staff == null || !AuthUtil.ROLE_STAFF.equals(staff.getRole())) {
            throw new BusinessException("staffPhone 对应用户不存在或角色不是 STAFF");
        }

        Store store = Store.builder()
                .name(request.getName())
                .creditCode(request.getCreditCode())
                .logo(request.getLogo())
                .build();
        save(store);

        staff.setStoreId(store.getId());
        userMapper.updateById(staff);

        cacheUtil.invalidateByPrefix(CacheKeys.STORE_LIST_PREFIX); // 新增商店，失效商店列表缓存（L1+L2）

        return CreateStoreResponse.builder()
                .storeId(store.getId())
                .build();
    }

    // ── 4. updateStore ───────────────────────────────────────────────────────
    @Override
    public void updateStore(Long storeId, UpdateStoreRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_ADMIN);

        Store store = getById(storeId);
        if (store == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "商店不存在");
        }
        if (request.getName() != null) {
            store.setName(request.getName());
        }
        if (request.getLogo() != null) {
            store.setLogo(request.getLogo());
        }
        updateById(store);

        // 商店信息变更，失效该商店详情 + 商店列表缓存（L1+L2）
        cacheUtil.invalidate(CacheKeys.storeDetail(storeId));
        cacheUtil.invalidateByPrefix(CacheKeys.STORE_LIST_PREFIX);
    }

    // ── 5. listAllStoresForAdmin ─────────────────────────────────────────────
    @Override
    public IPage<StoreListItemResponse> getAdminStoreList(int page, int size) {
        AuthUtil.requireRole(AuthUtil.ROLE_ADMIN);
        IPage<Store> storePage = this.page(new Page<>(page, size));
        return storePage.convert(store -> toListItem(store, true));
    }

    // ── 私有辅助 ─────────────────────────────────────────────────────────────
    private StoreListItemResponse toListItem(Store store, boolean includeCreditCode) {
        long count = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getStoreId, store.getId()));
        return StoreListItemResponse.builder()
                .id(store.getId())
                .name(store.getName())
                .logo(store.getLogo())
                .productCount((int) count)
                .creditCode(includeCreditCode ? store.getCreditCode() : null)
                .build();
    }
}
