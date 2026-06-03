package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CreateAddressRequest;
import com.twentyzhang.bluewhale.dto.UpdateAddressRequest;
import com.twentyzhang.bluewhale.dto.UserAddressResponse;
import com.twentyzhang.bluewhale.entity.UserAddress;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.UserAddressMapper;
import com.twentyzhang.bluewhale.service.AddressService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements AddressService {

    // ── 1. listAddresses ──────────────────────────────────────────────────────
    @Override
    public List<UserAddressResponse> getAddresses(Long userId) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        // 使用 QueryWrapper（字符串列名）而非 LambdaQueryWrapper，
        // 避免 ORDER BY 列名解析在无 Spring 上下文的单元测试中因 lambda cache 未初始化而失败。
        return list(new QueryWrapper<UserAddress>()
                .eq("user_id", userId)
                .orderByDesc("is_default")   // 1(默认) 排在 0 前面
                .orderByDesc("created_at"))  // 同为非默认时最新的在前
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── 2. createAddress ──────────────────────────────────────────────────────
    @Override
    @Transactional
    public Long addAddress(Long userId, CreateAddressRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        long existingCount = count(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));

        // 首条地址无论 isDefault 传什么，强制设为默认
        boolean willBeDefault = existingCount == 0 || Boolean.TRUE.equals(request.getIsDefault());

        if (willBeDefault && existingCount > 0) {
            clearDefaultForUser(userId, null);
        }

        UserAddress address = UserAddress.builder()
                .userId(userId)
                .receiverName(request.getReceiverName())
                .phone(request.getPhone())
                .province(request.getProvince())
                .city(request.getCity())
                .district(request.getDistrict())
                .detail(request.getDetail())
                .isDefault(willBeDefault ? 1 : 0)
                .build();
        save(address);
        return address.getId();
    }

    // ── 3. updateAddress ──────────────────────────────────────────────────────
    @Override
    @Transactional
    public void updateAddress(Long userId, Long addressId, UpdateAddressRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        UserAddress address = getById(addressId);
        if (address == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "地址不存在");
        }
        if (!userId.equals(address.getUserId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权操作该地址");
        }

        if (request.getReceiverName() != null) address.setReceiverName(request.getReceiverName());
        if (request.getPhone()        != null) address.setPhone(request.getPhone());
        if (request.getProvince()     != null) address.setProvince(request.getProvince());
        if (request.getCity()         != null) address.setCity(request.getCity());
        if (request.getDistrict()     != null) address.setDistrict(request.getDistrict());
        if (request.getDetail()       != null) address.setDetail(request.getDetail());
        if (request.getIsDefault()    != null) {
            address.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()) ? 1 : 0);
        }

        updateById(address);

        // 将该地址设为默认时，清除同用户其他地址的默认标记
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultForUser(userId, addressId);
        }
    }

    // ── 4. deleteAddress ──────────────────────────────────────────────────────
    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        UserAddress address = getById(addressId);
        if (address == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "地址不存在");
        }
        if (!userId.equals(address.getUserId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权操作该地址");
        }

        boolean wasDefault = Integer.valueOf(1).equals(address.getIsDefault());
        removeById(addressId); // 逻辑删除（@TableLogic）

        if (wasDefault) {
            // 将剩余地址中最新创建的一条设为新默认
            List<UserAddress> remaining = list(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId)
                    .orderByDesc(UserAddress::getCreateTime));
            if (!remaining.isEmpty()) {
                UserAddress newest = remaining.get(0);
                newest.setIsDefault(1);
                updateById(newest);
            }
        }
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

    /**
     * 将该用户除 excludeId 以外的所有默认地址重置为非默认。
     * excludeId 为 null 时不排除任何地址（用于新增地址场景）。
     */
    private void clearDefaultForUser(Long userId, Long excludeId) {
        // 使用 UpdateWrapper（字符串列名）而非 LambdaUpdateWrapper：
        // LambdaUpdateWrapper.set() 在构造时立即解析列名，需要 lambda cache（Spring 启动时注册），
        // 无 Spring 上下文的单元测试中会报 "can not find lambda cache"。
        update(new UpdateWrapper<UserAddress>()
                .eq("user_id", userId)
                .eq("is_default", 1)
                .ne(excludeId != null, "id", excludeId)
                .set("is_default", 0));
    }

    private UserAddressResponse toResponse(UserAddress a) {
        return UserAddressResponse.builder()
                .id(a.getId())
                .receiverName(a.getReceiverName())
                .phone(a.getPhone())
                .province(a.getProvince())
                .city(a.getCity())
                .district(a.getDistrict())
                .detail(a.getDetail())
                .isDefault(Integer.valueOf(1).equals(a.getIsDefault()))
                .build();
    }
}
