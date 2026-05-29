package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.CreateAddressRequest;
import com.twentyzhang.bluewhale.dto.IdResponse;
import com.twentyzhang.bluewhale.dto.UpdateAddressRequest;
import com.twentyzhang.bluewhale.dto.UserAddressResponse;
import com.twentyzhang.bluewhale.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public Result<List<UserAddressResponse>> getAddresses() {
        return Result.success(addressService.getAddresses(currentUser().userId()));
    }

    // 需要鉴权 仅 Customer
    @PostMapping
    public Result<IdResponse> addAddress(@RequestBody @Valid CreateAddressRequest request) {
        return Result.success(IdResponse.builder().id(addressService.addAddress(currentUser().userId(), request)).build());
    }

    // 需要鉴权 仅 Customer（本人地址）
    @PutMapping("/{addressId}")
    public Result<Void> updateAddress(@PathVariable Long addressId,
                                      @RequestBody UpdateAddressRequest request) {
        addressService.updateAddress(currentUser().userId(), addressId, request);
        return Result.success();
    }

    // 需要鉴权 仅 Customer（本人地址）
    @DeleteMapping("/{addressId}")
    public Result<Void> deleteAddress(@PathVariable Long addressId) {
        addressService.deleteAddress(currentUser().userId(), addressId);
        return Result.success();
    }

    private AuthUser currentUser() {
        return (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
