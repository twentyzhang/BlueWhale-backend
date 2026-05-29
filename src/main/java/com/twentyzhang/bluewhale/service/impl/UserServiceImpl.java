package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.ChangePasswordRequest;
import com.twentyzhang.bluewhale.dto.LoginRequest;
import com.twentyzhang.bluewhale.dto.LoginResponse;
import com.twentyzhang.bluewhale.dto.RegisterRequest;
import com.twentyzhang.bluewhale.dto.UpdateProfileRequest;
import com.twentyzhang.bluewhale.dto.UserInfoResponse;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.service.UserService;
import com.twentyzhang.bluewhale.util.JwtUtil;
import com.twentyzhang.bluewhale.util.RedisUtil;
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
                .role(request.getRole())
                .build();
        save(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = baseMapper.selectByPhone(request.getPhone());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("手机号或密码不正确");
        }
        String accessToken  = jwtUtil.generateToken(user.getId(), user.getRole(), user.getStoreId());
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
    }

    private String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
