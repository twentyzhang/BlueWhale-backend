package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.ChangePasswordRequest;
import com.twentyzhang.bluewhale.dto.LoginRequest;
import com.twentyzhang.bluewhale.dto.LoginResponse;
import com.twentyzhang.bluewhale.dto.RefreshTokenRequest;
import com.twentyzhang.bluewhale.dto.RefreshTokenResponse;
import com.twentyzhang.bluewhale.dto.RegisterRequest;
import com.twentyzhang.bluewhale.dto.UpdateProfileRequest;
import com.twentyzhang.bluewhale.dto.UserInfoResponse;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.service.UserService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.JwtUtil;
import com.twentyzhang.bluewhale.util.RedisUtil;
import com.twentyzhang.bluewhale.util.TokenVersionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final TokenVersionStore tokenVersionStore;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final long REFRESH_TOKEN_DAYS = 7;

    @Override
    public void register(RegisterRequest request) {
        if (baseMapper.selectByPhone(request.getPhone()) != null) {
            throw new BusinessException("该手机号已被注册");
        }
        User user = User.builder()
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                // Security: always assign CUSTOMER on self-registration; ignore client-supplied role
                // to prevent privilege escalation. Privileged roles (ADMIN/STAFF) are provisioned
                // only via seed data (R__seed_data.sql) or backend admin tooling.
                .role(AuthUtil.ROLE_CUSTOMER)
                .build();
        save(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = baseMapper.selectByPhone(request.getPhone());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("手机号或密码不正确");
        }
        String accessToken  = jwtUtil.generateToken(user.getId(), user.getRole(), user.getStoreId(),
                tokenVersionStore.current(user.getId()));
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        redisUtil.setWithExpire(REFRESH_TOKEN_PREFIX + user.getId(), refreshToken,
                REFRESH_TOKEN_DAYS, TimeUnit.DAYS);
        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }

    @Override
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        String storedToken = redisUtil.get(REFRESH_TOKEN_PREFIX + request.getUserId());
        if (storedToken == null || !storedToken.equals(request.getRefreshToken())) {
            // refreshToken 不存在（已过期/已登出）或与服务端记录不一致
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "refreshToken 无效或已过期，请重新登录");
        }
        User user = getById(request.getUserId());
        if (user == null) {
            throw new BusinessException(Result.CODE_UNAUTHORIZED, "用户不存在，请重新登录");
        }
        String accessToken     = jwtUtil.generateToken(user.getId(), user.getRole(), user.getStoreId(),
                tokenVersionStore.current(user.getId()));
        String newRefreshToken = UUID.randomUUID().toString().replace("-", "");
        // 轮换 refreshToken 并重置 7 天有效期，旧 token 立即失效
        redisUtil.setWithExpire(REFRESH_TOKEN_PREFIX + user.getId(), newRefreshToken,
                REFRESH_TOKEN_DAYS, TimeUnit.DAYS);
        return RefreshTokenResponse.builder()
                .token(accessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Override
    public UserInfoResponse getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return UserInfoResponse.builder()
                .id(user.getId())
                .phone(maskPhone(user.getPhone()))
                .nickname(user.getNickname())
                .role(user.getRole())
                .storeId(user.getStoreId())
                .build();
    }

    @Override
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setNickname(request.getNickname());
        updateById(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        updateById(user);

        // 改密后撤销该用户全部已签发令牌：自增令牌版本 + 删除 refreshToken，强制重新登录
        tokenVersionStore.bump(userId);
        redisUtil.delete(REFRESH_TOKEN_PREFIX + userId);
    }

    @Override
    public void logout(Long userId) {
        // 自增令牌版本使 access token 立即失效 + 删除 refreshToken 阻断刷新
        tokenVersionStore.bump(userId);
        redisUtil.delete(REFRESH_TOKEN_PREFIX + userId);
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
