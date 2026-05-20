package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.CreateStoreRequest;
import com.twentyzhang.bluewhale.dto.CreateStoreResponse;
import com.twentyzhang.bluewhale.dto.StoreDetailResponse;
import com.twentyzhang.bluewhale.dto.StoreListItemResponse;
import com.twentyzhang.bluewhale.dto.UpdateStoreRequest;
import com.twentyzhang.bluewhale.entity.Store;
import com.twentyzhang.bluewhale.mapper.StoreMapper;
import com.twentyzhang.bluewhale.service.StoreService;
import org.springframework.stereotype.Service;

@Service
public class StoreServiceImpl extends ServiceImpl<StoreMapper, Store> implements StoreService {

    @Override
    public IPage<StoreListItemResponse> getStoreList(int page, int size) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public StoreDetailResponse getStoreById(Long storeId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public CreateStoreResponse createStore(CreateStoreRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void updateStore(Long storeId, UpdateStoreRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public IPage<StoreListItemResponse> getAdminStoreList(int page, int size) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
