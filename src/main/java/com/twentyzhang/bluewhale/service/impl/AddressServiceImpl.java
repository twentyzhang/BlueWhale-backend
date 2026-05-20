package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.CreateAddressRequest;
import com.twentyzhang.bluewhale.dto.UpdateAddressRequest;
import com.twentyzhang.bluewhale.dto.UserAddressResponse;
import com.twentyzhang.bluewhale.entity.UserAddress;
import com.twentyzhang.bluewhale.mapper.UserAddressMapper;
import com.twentyzhang.bluewhale.service.AddressService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements AddressService {

    @Override
    public List<UserAddressResponse> getAddresses(Long userId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Long addAddress(Long userId, CreateAddressRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void updateAddress(Long userId, Long addressId, UpdateAddressRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
