package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.config.AiMetrics;
import com.twentyzhang.bluewhale.config.SecurityConfig;
import com.twentyzhang.bluewhale.dto.StoreDetailResponse;
import com.twentyzhang.bluewhale.dto.StoreListItemResponse;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.filter.JwtAuthenticationFilter;
import com.twentyzhang.bluewhale.service.StoreService;
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
        controllers = StoreController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}
        )
)
@DisplayName("StoreController")
class StoreControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StoreService storeService;

    @MockitoBean
    private RateLimitUtil rateLimitUtil;

    @MockitoBean
    private AiMetrics aiMetrics;

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
    // GET /api/stores
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/stores")
    class GetStoreList {

        @Test
        @DisplayName("正常返回分页数据，code=200，records 非空")
        void success_returnsPaginatedData() throws Exception {
            Page<StoreListItemResponse> page = new Page<>(1, 10);
            page.setRecords(List.of(
                    StoreListItemResponse.builder()
                            .id(1L).name("南鲸旗舰店").logo("logo.png").productCount(8).build()
            ));
            page.setTotal(1L);

            when(storeService.getStoreList(1, 10)).thenReturn(page);

            mvc.perform(get("/api/stores"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.records[0].name").value("南鲸旗舰店"))
                    .andExpect(jsonPath("$.data.records[0].productCount").value(8));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/stores/{storeId}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/stores/{storeId}")
    class GetStoreById {

        @Test
        @DisplayName("商店不存在时 Service 抛 404 异常，返回 code=404")
        void notFound_returns404() throws Exception {
            when(storeService.getStoreById(999L))
                    .thenThrow(new BusinessException(Result.CODE_NOT_FOUND, "商店不存在"));

            mvc.perform(get("/api/stores/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("商店不存在"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/stores
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/stores")
    class CreateStore {

        @Test
        @DisplayName("非 Admin 用户调用时 Service 抛 403 异常，返回 code=403")
        void notAdmin_returns403() throws Exception {
            setupAuth(1L, "CUSTOMER");
            // Service 内部 AuthUtil.requireRole(ADMIN) 抛出 403；
            // 在 Controller 测试中直接 mock service 抛出，验证 Controller → ExceptionHandler 管道
            when(storeService.createStore(any()))
                    .thenThrow(new BusinessException(Result.CODE_FORBIDDEN, "权限不足"));

            mvc.perform(post("/api/stores")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"南鲸旗舰店",
                                      "creditCode":"91320100XXXXX",
                                      "logo":"logo.png",
                                      "staffPhone":"13900139000"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("权限不足"));
        }
    }
}
