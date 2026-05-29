package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.ChangePasswordRequest;
import com.twentyzhang.bluewhale.dto.UpdateProfileRequest;
import com.twentyzhang.bluewhale.dto.UserInfoResponse;
import com.twentyzhang.bluewhale.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @GetMapping("/me")
    public Result<UserInfoResponse> getMe() {
        return Result.success(userService.getUserInfo(currentUser().userId()));
    }

    @PutMapping("/me")
    public Result<Void> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        userService.updateProfile(currentUser().userId(), request);
        return Result.success();
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        userService.changePassword(currentUser().userId(), request);
        return Result.success();
    }

    private AuthUser currentUser() {
        return (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
