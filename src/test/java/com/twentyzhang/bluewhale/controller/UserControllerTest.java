package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.config.SecurityConfig;
import com.twentyzhang.bluewhale.dto.LoginResponse;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.filter.JwtAuthenticationFilter;
import com.twentyzhang.bluewhale.service.UserService;
import com.twentyzhang.bluewhale.util.RateLimitUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = {AuthController.class, UserController.class},
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}
        )
)
@DisplayName("UserController / AuthController")
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RateLimitUtil rateLimitUtil;

    /** 为需要鉴权的测试设置 SecurityContext。 */
    private void setupAuth(Long userId, String role) {
        AuthUser authUser = new AuthUser(userId, role, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authUser, null, List.of(new SimpleGrantedAuthority(role))));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/register
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("手机号为空时返回 code=400")
        void phoneBlank_returns400() throws Exception {
            mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"","password":"Abc123456","nickname":"张三","role":"CUSTOMER"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("密码长度不足 6 位时返回 code=400")
        void passwordTooShort_returns400() throws Exception {
            mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"13800138000","password":"123","nickname":"张三","role":"CUSTOMER"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/login
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("凭证正确时返回 code=200 且 data.token 存在")
        void success_returns200WithToken() throws Exception {
            LoginResponse resp = LoginResponse.builder()
                    .token("jwt.access.token")
                    .refreshToken("uuid-refresh-token")
                    .userId(1L).nickname("张三").role("CUSTOMER")
                    .build();
            when(userService.login(any())).thenReturn(resp);

            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"13800138000","password":"Abc123456"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("jwt.access.token"))
                    .andExpect(jsonPath("$.data.userId").value(1));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/users/me
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/users/me")
    class GetMe {

        @Test
        @DisplayName("未携带 Token 时返回 code=401（AuthUtil 抛 BusinessException）")
        void noToken_returns401() throws Exception {
            // SecurityContext 为空 → AuthUtil.getCurrentUser() 抛 BusinessException(401)
            // → GlobalExceptionHandler 返回 code=401
            mvc.perform(get("/api/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/users/me/password
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/users/me/password")
    class ChangePassword {

        @Test
        @DisplayName("新旧密码相同时 Service 抛业务异常，返回 code=400")
        void samePassword_returns400() throws Exception {
            setupAuth(1L, "CUSTOMER");
            doThrow(new BusinessException("新密码不能与旧密码相同"))
                    .when(userService).changePassword(eq(1L), any());

            mvc.perform(put("/api/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"oldPassword":"same123","newPassword":"same123","confirmPassword":"same123"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("新密码不能与旧密码相同"));
        }
    }
}
