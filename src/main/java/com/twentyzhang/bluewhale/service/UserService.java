package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.dto.ChangePasswordRequest;
import com.twentyzhang.bluewhale.dto.LoginRequest;
import com.twentyzhang.bluewhale.dto.LoginResponse;
import com.twentyzhang.bluewhale.dto.RefreshTokenRequest;
import com.twentyzhang.bluewhale.dto.RefreshTokenResponse;
import com.twentyzhang.bluewhale.dto.RegisterRequest;
import com.twentyzhang.bluewhale.dto.UpdateProfileRequest;
import com.twentyzhang.bluewhale.dto.UserInfoResponse;
import com.twentyzhang.bluewhale.entity.User;

public interface UserService extends IService<User> {

    /**
     * 用户注册。校验手机号格式、密码强度、手机号唯一性，通过后持久化新用户。
     * 对应 POST /api/auth/register
     */
    void register(RegisterRequest request);

    /**
     * 用户登录。校验手机号与密码，通过后生成 JWT Token 并返回用户基本信息。
     * 对应 POST /api/auth/login
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用 refreshToken 换取新的 accessToken。
     * 校验 refreshToken 与 Redis 中保存的一致后，签发新 accessToken 并轮换 refreshToken（重置 7 天有效期）。
     * 对应 POST /api/auth/refresh
     */
    RefreshTokenResponse refreshToken(RefreshTokenRequest request);

    /**
     * 获取当前登录用户信息（手机号脱敏）。
     * 对应 GET /api/users/me
     */
    UserInfoResponse getUserInfo(Long userId);

    /**
     * 修改当前用户昵称。
     * 对应 PUT /api/users/me
     */
    void updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * 修改当前用户密码。校验旧密码正确性及两次新密码一致性。
     * 对应 PUT /api/users/me/password
     */
    void changePassword(Long userId, ChangePasswordRequest request);
}
