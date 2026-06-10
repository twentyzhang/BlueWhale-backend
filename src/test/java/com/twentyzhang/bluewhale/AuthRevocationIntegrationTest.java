package com.twentyzhang.bluewhale;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 E：登录态即时撤销端到端校验（需 MySQL + Redis）。
 * 用种子管理员账号登录 → 访问受保护接口成功 → 登出 → 同一 access token 立即失效（401）。
 * 覆盖 JwtUtil 的 ver 声明、过滤器版本校验、logout 自增令牌版本的全链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("认证即时撤销（登出）")
class AuthRevocationIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 用手机号/密码登录，返回 access token。 */
    private String login(String phone, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("token").asText();
    }

    @Test
    @DisplayName("登出后旧 access token 立即失效（401）")
    void logout_revokesAccessTokenImmediately() throws Exception {
        // 种子管理员账号（见 R__seed_data.sql / 数据库迁移指南）
        String token = login("13000000000", "Admin@123456");

        // 1) 登出前：访问受保护接口成功
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 2) 登出（自增令牌版本 + 删除 refreshToken）
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 3) 登出后：同一 access token 立即失效 → 401
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
