package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.dto.CreateAddressRequest;
import com.twentyzhang.bluewhale.dto.UpdateAddressRequest;
import com.twentyzhang.bluewhale.dto.UserAddressResponse;
import com.twentyzhang.bluewhale.entity.UserAddress;

import java.util.List;

public interface AddressService extends IService<UserAddress> {

    /**
     * 获取当前用户的所有收货地址列表。
     * 对应 GET /api/addresses
     */
    List<UserAddressResponse> getAddresses(Long userId);

    /**
     * 新增收货地址。若 isDefault=true，则将该用户其他地址的 isDefault 重置为 false。
     * 对应 POST /api/addresses
     *
     * @return 新建地址 ID
     */
    Long addAddress(Long userId, CreateAddressRequest request);

    /**
     * 修改指定收货地址。校验地址属于当前用户。
     * 若 isDefault=true，则同步将该用户其他地址的 isDefault 重置为 false。
     * 对应 PUT /api/addresses/{addressId}
     */
    void updateAddress(Long userId, Long addressId, UpdateAddressRequest request);

    /**
     * 逻辑删除指定收货地址。校验地址属于当前用户。
     * 对应 DELETE /api/addresses/{addressId}
     */
    void deleteAddress(Long userId, Long addressId);
}
