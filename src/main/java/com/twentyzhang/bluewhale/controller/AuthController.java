package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.LoginRequest;
import com.twentyzhang.bluewhale.dto.LoginResponse;
import com.twentyzhang.bluewhale.dto.RefreshTokenRequest;
import com.twentyzhang.bluewhale.dto.RefreshTokenResponse;
import com.twentyzhang.bluewhale.dto.RegisterRequest;
import com.twentyzhang.bluewhale.service.UserService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterRequest request) {
        userService.register(request);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @PostMapping("/refresh")
    public Result<RefreshTokenResponse> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return Result.success(userService.refreshToken(request));
    }

    /**
     * 退出登录：撤销当前用户全部已签发令牌（自增令牌版本 + 删除 refreshToken）。
     * 需携带有效 access token（从中解析当前用户）。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout(AuthUtil.getCurrentUserId());
        return Result.success();
    }
}
