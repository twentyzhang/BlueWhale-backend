package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.ChangePasswordRequest;
import com.twentyzhang.bluewhale.dto.LoginRequest;
import com.twentyzhang.bluewhale.dto.LoginResponse;
import com.twentyzhang.bluewhale.dto.RegisterRequest;
import com.twentyzhang.bluewhale.dto.UpdateProfileRequest;
import com.twentyzhang.bluewhale.dto.UserInfoResponse;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public void register(RegisterRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public UserInfoResponse getUserInfo(Long userId) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
