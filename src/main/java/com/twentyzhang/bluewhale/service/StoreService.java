package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.dto.CreateStoreRequest;
import com.twentyzhang.bluewhale.dto.CreateStoreResponse;
import com.twentyzhang.bluewhale.dto.StoreDetailResponse;
import com.twentyzhang.bluewhale.dto.StoreListItemResponse;
import com.twentyzhang.bluewhale.dto.UpdateStoreRequest;
import com.twentyzhang.bluewhale.entity.Store;

public interface StoreService extends IService<Store> {

    /**
     * 分页获取所有商店列表（游客/顾客可访问）。
     * 对应 GET /api/stores
     */
    IPage<StoreListItemResponse> getStoreList(int page, int size);

    /**
     * 获取指定商店详情。
     * 对应 GET /api/stores/{storeId}
     */
    StoreDetailResponse getStoreById(Long storeId);

    /**
     * 创建新商店，并将指定手机号的 Staff 账号关联到该商店。
     * 仅 Admin 可调用。
     * 对应 POST /api/stores
     */
    CreateStoreResponse createStore(CreateStoreRequest request);

    /**
     * 修改商店基本信息（名称、Logo）。
     * 仅 Admin 可调用。
     * 对应 PUT /api/stores/{storeId}
     */
    void updateStore(Long storeId, UpdateStoreRequest request);

    /**
     * 管理员分页查看所有商店（含统一社会信用代码）。
     * 仅 Admin 可调用。
     * 对应 GET /api/admin/stores
     */
    IPage<StoreListItemResponse> getAdminStoreList(int page, int size);
}
