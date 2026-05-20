package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.CreateAddressRequest;
import com.twentyzhang.bluewhale.dto.IdResponse;
import com.twentyzhang.bluewhale.dto.UpdateAddressRequest;
import com.twentyzhang.bluewhale.dto.UserAddressResponse;
import com.twentyzhang.bluewhale.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    // 需要鉴权 仅 Customer
    @GetMapping
    public ApiResponse<List<UserAddressResponse>> getAddresses() {
        return ApiResponse.success(addressService.getAddresses(getCurrentUserId()));
    }

    // 需要鉴权 仅 Customer
    @PostMapping
    public ApiResponse<IdResponse> addAddress(@RequestBody @Valid CreateAddressRequest request) {
        return ApiResponse.success(IdResponse.builder().id(addressService.addAddress(getCurrentUserId(), request)).build());
    }

    // 需要鉴权 仅 Customer（本人地址）
    @PutMapping("/{addressId}")
    public ApiResponse<Void> updateAddress(@PathVariable Long addressId,
                                            @RequestBody UpdateAddressRequest request) {
        addressService.updateAddress(getCurrentUserId(), addressId, request);
        return ApiResponse.success();
    }

    // 需要鉴权 仅 Customer（本人地址）
    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> deleteAddress(@PathVariable Long addressId) {
        addressService.deleteAddress(getCurrentUserId(), addressId);
        return ApiResponse.success();
    }

    private Long getCurrentUserId() {
        // TODO: 从 JWT Token 解析 userId
        return null;
    }
}
