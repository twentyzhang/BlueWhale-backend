package com.twentyzhang.bluewhale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 A：HTTP 语义修正的端到端校验（需 MySQL + Redis）。
 * 验证未登录返回 401（而非 403）、非法请求体返回 400（而非 500），响应体均为统一 Result 结构。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Security/HTTP 语义")
class SecuritySemanticsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("未登录访问受保护接口 → 401（不再是 403）")
    void unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("非法 JSON 请求体 → 业务码 400（不再落入 500）")
    void malformedBody_returns400() throws Exception {
        // @RestControllerAdvice 约定：HTTP 200 + 响应体 code 语义化（与其余校验错误一致）
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
