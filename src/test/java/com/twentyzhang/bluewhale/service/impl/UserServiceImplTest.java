package com.twentyzhang.bluewhale.service.impl;

import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.dto.*;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.util.JwtUtil;
import com.twentyzhang.bluewhale.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("UserServiceImpl 单元测试")
class UserServiceImplTest extends BaseServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private UserServiceImpl userService;

    private static User anyUser() {
        return ArgumentMatchers.any(User.class);
    }

    @BeforeEach
    void setUp() {
        // ServiceImpl 通过继承持有 baseMapper，Mockito 字段注入可能因泛型擦除失效，
        // 此处显式注入保证可靠。
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("注册成功：手机号未被使用时保存用户")
    void register_success() {
        RegisterRequest req = new RegisterRequest();
        req.setPhone("13800138000");
        req.setPassword("Abc123456");
        req.setNickname("张三");
        req.setRole("CUSTOMER");

        when(userMapper.selectByPhone("13800138000")).thenReturn(null);
        when(passwordEncoder.encode("Abc123456")).thenReturn("hashed");
        when(userMapper.insert(any(User.class))).thenReturn(1);

        assertDoesNotThrow(() -> userService.register(req));
        verify(userMapper).insert(any(User.class));
    }

    @Test
    @DisplayName("注册失败：手机号已被注册")
    void register_duplicatePhone() {
        RegisterRequest req = new RegisterRequest();
        req.setPhone("13800138000");

        when(userMapper.selectByPhone("13800138000")).thenReturn(new User());

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.register(req));
        assertEquals("该手机号已被注册", ex.getMessage());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("登录成功：凭证正确时返回 Token 及用户信息")
    void login_success() {
        LoginRequest req = new LoginRequest();
        req.setPhone("13800138000");
        req.setPassword("Abc123456");

        User user = User.builder()
                .id(1L).phone("13800138000").password("hashed")
                .nickname("张三").role("CUSTOMER").build();

        when(userMapper.selectByPhone("13800138000")).thenReturn(user);
        when(passwordEncoder.matches("Abc123456", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "CUSTOMER", null)).thenReturn("jwt.token");

        LoginResponse resp = userService.login(req);

        assertEquals("jwt.token", resp.getToken());
        assertEquals(1L, resp.getUserId());
        assertEquals("张三", resp.getNickname());
        assertEquals("CUSTOMER", resp.getRole());
        verify(redisUtil).setWithExpire(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("登录失败：手机号不存在")
    void login_userNotFound() {
        LoginRequest req = new LoginRequest();
        req.setPhone("13800138000");
        req.setPassword("Abc123456");

        when(userMapper.selectByPhone("13800138000")).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.login(req));
    }

    @Test
    @DisplayName("登录失败：密码不正确")
    void login_wrongPassword() {
        LoginRequest req = new LoginRequest();
        req.setPhone("13800138000");
        req.setPassword("wrong");

        User user = User.builder().phone("13800138000").password("hashed").build();
        when(userMapper.selectByPhone("13800138000")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(BusinessException.class, () -> userService.login(req));
    }

    // ── getUserInfo ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("获取用户信息：返回脱敏手机号")
    void getUserInfo_success() {
        User user = User.builder()
                .id(1L).phone("13800138000").nickname("张三")
                .role("CUSTOMER").storeId(null).build();

        when(userMapper.selectById(1L)).thenReturn(user);

        UserInfoResponse resp = userService.getUserInfo(1L);

        assertEquals(1L, resp.getId());
        assertEquals("138****8000", resp.getPhone());
        assertEquals("张三", resp.getNickname());
        assertNull(resp.getStoreId());
    }

    @Test
    @DisplayName("获取用户信息：用户不存在时抛 BusinessException")
    void getUserInfo_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.getUserInfo(99L));
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("修改个人信息：昵称更新成功")
    void updateProfile_success() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNickname("新昵称");

        User user = User.builder().id(1L).nickname("旧昵称").build();
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(anyUser())).thenReturn(1);

        assertDoesNotThrow(() -> userService.updateProfile(1L, req));
        assertEquals("新昵称", user.getNickname());
    }

    @Test
    @DisplayName("修改个人信息：用户不存在时抛 BusinessException")
    void updateProfile_userNotFound() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> userService.updateProfile(99L, new UpdateProfileRequest()));
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    @DisplayName("修改密码：旧密码正确且两次新密码一致时成功")
    void changePassword_success() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old123");
        req.setNewPassword("New456789");
        req.setConfirmPassword("New456789");

        User user = User.builder().id(1L).password("hashed_old").build();
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("old123", "hashed_old")).thenReturn(true);
        when(passwordEncoder.encode("New456789")).thenReturn("hashed_new");
        when(userMapper.updateById(anyUser())).thenReturn(1);

        assertDoesNotThrow(() -> userService.changePassword(1L, req));
        verify(passwordEncoder).encode("New456789");
    }

    @Test
    @DisplayName("修改密码：两次新密码不一致时抛 BusinessException")
    void changePassword_passwordMismatch() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old123");
        req.setNewPassword("New456789");
        req.setConfirmPassword("Different");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(1L, req));
        assertEquals("两次输入的新密码不一致", ex.getMessage());
    }

    @Test
    @DisplayName("修改密码：旧密码错误时抛 BusinessException")
    void changePassword_wrongOldPassword() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("wrong");
        req.setNewPassword("New456789");
        req.setConfirmPassword("New456789");

        User user = User.builder().id(1L).password("hashed_old").build();
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed_old")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(1L, req));
        assertEquals("旧密码不正确", ex.getMessage());
    }
}
