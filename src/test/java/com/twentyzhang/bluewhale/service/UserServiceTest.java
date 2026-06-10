package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.dto.*;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.service.impl.UserServiceImpl;
import com.twentyzhang.bluewhale.util.JwtUtil;
import com.twentyzhang.bluewhale.util.RedisUtil;
import com.twentyzhang.bluewhale.util.TokenVersionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("UserService")
class UserServiceTest extends BaseServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private TokenVersionStore tokenVersionStore;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        // ServiceImpl 继承的 baseMapper 字段因泛型擦除无法由 Mockito 自动注入，手动设置。
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // register
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("手机号未注册时注册成功，用户被持久化")
        void success() {
            RegisterRequest req = new RegisterRequest();
            req.setPhone("13800138000");
            req.setPassword("Abc123456");
            req.setNickname("张三");
            req.setRole("CUSTOMER");

            when(userMapper.selectByPhone("13800138000")).thenReturn(null);
            when(passwordEncoder.encode("Abc123456")).thenReturn("hashed");
            // (User) any() 强制编译器选择 insert(T entity) 重载，
            // 避免与 3.5.9 新增的 insert(Collection<T>) 产生歧义。
            when(userMapper.insert((User) any())).thenReturn(1);

            assertDoesNotThrow(() -> userService.register(req));

            // ArgumentCaptor 替代 argThat lambda，绕开泛型 T 导致方法不可见的问题
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert((User) captor.capture());
            User saved = captor.getValue();
            assertEquals("13800138000", saved.getPhone());
            assertEquals("hashed",      saved.getPassword());
            assertEquals("张三",         saved.getNickname());
            assertEquals("CUSTOMER",    saved.getRole());
        }

        @Test
        @DisplayName("手机号已存在时抛出 BusinessException")
        void duplicatePhone() {
            RegisterRequest req = new RegisterRequest();
            req.setPhone("13800138000");

            when(userMapper.selectByPhone("13800138000")).thenReturn(new User());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.register(req));
            assertEquals("该手机号已被注册", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // login
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("凭证正确时返回含 accessToken 和 refreshToken 的响应")
        void success() {
            LoginRequest req = new LoginRequest();
            req.setPhone("13800138000");
            req.setPassword("Abc123456");

            User user = User.builder()
                    .id(1L).phone("13800138000").password("hashed")
                    .nickname("张三").role("CUSTOMER").storeId(null).build();

            when(userMapper.selectByPhone("13800138000")).thenReturn(user);
            when(passwordEncoder.matches("Abc123456", "hashed")).thenReturn(true);
            when(jwtUtil.generateToken(1L, "CUSTOMER", null, 0L)).thenReturn("access.jwt.token");

            LoginResponse resp = userService.login(req);

            // accessToken 由 JwtUtil mock 生成
            assertEquals("access.jwt.token", resp.getToken());
            // refreshToken 由 UUID 生成，只验证非空
            assertNotNull(resp.getRefreshToken());
            assertFalse(resp.getRefreshToken().isBlank());
            // 验证 refreshToken 已原样写入 Redis
            verify(redisUtil).setWithExpire(
                    eq("refresh_token:1"),
                    eq(resp.getRefreshToken()),
                    anyLong(),
                    any()
            );
        }

        @Test
        @DisplayName("手机号不存在时抛出 BusinessException")
        void phoneNotFound() {
            LoginRequest req = new LoginRequest();
            req.setPhone("13800138000");
            req.setPassword("Abc123456");

            when(userMapper.selectByPhone("13800138000")).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.login(req));
            assertEquals("手机号或密码不正确", ex.getMessage());
        }

        @Test
        @DisplayName("密码错误时抛出 BusinessException")
        void wrongPassword() {
            LoginRequest req = new LoginRequest();
            req.setPhone("13800138000");
            req.setPassword("wrong");

            User user = User.builder()
                    .id(1L).phone("13800138000").password("hashed").build();

            when(userMapper.selectByPhone("13800138000")).thenReturn(user);
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.login(req));
            assertEquals("手机号或密码不正确", ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getUserInfo
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserInfo")
    class GetUserInfo {

        @Test
        @DisplayName("用户存在时返回正确信息")
        void success() {
            User user = User.builder()
                    .id(1L).phone("13800138000").nickname("张三")
                    .role("CUSTOMER").storeId(null).build();

            when(userMapper.selectById(1L)).thenReturn(user);

            UserInfoResponse resp = userService.getUserInfo(1L);

            assertEquals(1L, resp.getId());
            assertEquals("张三", resp.getNickname());
            assertEquals("CUSTOMER", resp.getRole());
            assertNull(resp.getStoreId());
        }

        @Test
        @DisplayName("手机号脱敏：格式为 138****8000")
        void phoneMasked() {
            User user = User.builder()
                    .id(1L).phone("13800138000").nickname("张三").role("CUSTOMER").build();

            when(userMapper.selectById(1L)).thenReturn(user);

            UserInfoResponse resp = userService.getUserInfo(1L);

            assertEquals("138****8000", resp.getPhone());
            // 前3位保留、4-7位掩码、后4位保留
            assertTrue(resp.getPhone().startsWith("138"));
            assertTrue(resp.getPhone().endsWith("8000"));
            assertEquals(4, resp.getPhone().chars().filter(c -> c == '*').count());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // changePassword
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("旧密码正确且两次新密码一致时修改成功")
        void success() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("old123");
            req.setNewPassword("New456789");
            req.setConfirmPassword("New456789");

            User user = User.builder().id(1L).password("hashed_old").build();

            when(userMapper.selectById(1L)).thenReturn(user);
            when(passwordEncoder.matches("old123", "hashed_old")).thenReturn(true);
            when(passwordEncoder.encode("New456789")).thenReturn("hashed_new");
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            assertDoesNotThrow(() -> userService.changePassword(1L, req));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).updateById(captor.capture());
            assertEquals("hashed_new", captor.getValue().getPassword());
        }

        @Test
        @DisplayName("旧密码错误时抛出 BusinessException")
        void wrongOldPassword() {
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

        @Test
        @DisplayName("新密码与确认密码不一致时抛出 BusinessException（不查库）")
        void passwordMismatch() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("old123");
            req.setNewPassword("New456789");
            req.setConfirmPassword("Different");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> userService.changePassword(1L, req));
            assertEquals("两次输入的新密码不一致", ex.getMessage());
            // 密码不一致应在查库前就失败
            verify(userMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("改密成功后撤销令牌：自增版本 + 删除 refreshToken")
        void success_revokesTokens() {
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setOldPassword("old123");
            req.setNewPassword("New456789");
            req.setConfirmPassword("New456789");

            User user = User.builder().id(1L).password("hashed_old").build();
            when(userMapper.selectById(1L)).thenReturn(user);
            when(passwordEncoder.matches("old123", "hashed_old")).thenReturn(true);
            when(passwordEncoder.encode("New456789")).thenReturn("hashed_new");
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            userService.changePassword(1L, req);

            verify(tokenVersionStore).bump(1L);
            verify(redisUtil).delete("refresh_token:1");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // logout
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("自增令牌版本并删除 refreshToken")
        void bumpsVersionAndDeletesRefresh() {
            userService.logout(1L);

            verify(tokenVersionStore).bump(1L);
            verify(redisUtil).delete("refresh_token:1");
        }
    }
}
