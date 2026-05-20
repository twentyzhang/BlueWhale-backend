package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.CreateStoreRequest;
import com.twentyzhang.bluewhale.dto.CreateStoreResponse;
import com.twentyzhang.bluewhale.dto.StoreDetailResponse;
import com.twentyzhang.bluewhale.dto.StoreListItemResponse;
import com.twentyzhang.bluewhale.dto.UpdateStoreRequest;
import com.twentyzhang.bluewhale.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public ApiResponse<IPage<StoreListItemResponse>> getStoreList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(storeService.getStoreList(page, size));
    }

    @GetMapping("/{storeId}")
    public ApiResponse<StoreDetailResponse> getStoreById(@PathVariable Long storeId) {
        return ApiResponse.success(storeService.getStoreById(storeId));
    }

    // 需要鉴权 仅 Admin
    @PostMapping
    public ApiResponse<CreateStoreResponse> createStore(@RequestBody @Valid CreateStoreRequest request) {
        return ApiResponse.success(storeService.createStore(request));
    }

    // 需要鉴权 仅 Admin
    @PutMapping("/{storeId}")
    public ApiResponse<Void> updateStore(@PathVariable Long storeId,
                                         @RequestBody UpdateStoreRequest request) {
        storeService.updateStore(storeId, request);
        return ApiResponse.success();
    }
}
