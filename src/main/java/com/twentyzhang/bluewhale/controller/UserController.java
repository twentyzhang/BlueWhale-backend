package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.dto.ApiResponse;
import com.twentyzhang.bluewhale.dto.ChangePasswordRequest;
import com.twentyzhang.bluewhale.dto.UpdateProfileRequest;
import com.twentyzhang.bluewhale.dto.UserInfoResponse;
import com.twentyzhang.bluewhale.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 需要鉴权
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> getMe() {
        return ApiResponse.success(userService.getUserInfo(getCurrentUserId()));
    }

    // 需要鉴权
    @PutMapping("/me")
    public ApiResponse<Void> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        userService.updateProfile(getCurrentUserId(), request);
        return ApiResponse.success();
    }

    // 需要鉴权
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        userService.changePassword(getCurrentUserId(), request);
        return ApiResponse.success();
    }

    private Long getCurrentUserId() {
        // TODO: 从 JWT Token 解析 userId
        return null;
    }
}
