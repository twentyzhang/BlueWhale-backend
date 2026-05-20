package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.CouponGroupResponse;
import com.twentyzhang.bluewhale.dto.CreateCouponGroupRequest;
import com.twentyzhang.bluewhale.entity.CouponGroup;
import com.twentyzhang.bluewhale.mapper.CouponGroupMapper;
import com.twentyzhang.bluewhale.service.CouponGroupService;
import org.springframework.stereotype.Service;

@Service
public class CouponGroupServiceImpl extends ServiceImpl<CouponGroupMapper, CouponGroup>
        implements CouponGroupService {

    @Override
    public IPage<CouponGroupResponse> getAvailableCouponGroups(int page, int size) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Long createStoreCouponGroup(Long storeId, CreateCouponGroupRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Long createGlobalCouponGroup(CreateCouponGroupRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void deleteCouponGroup(Long operatorStoreId, String operatorRole, Long groupId) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
