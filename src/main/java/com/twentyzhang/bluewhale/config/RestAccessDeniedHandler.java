package com.twentyzhang.bluewhale.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 已认证但无权限访问时的处理器，返回统一 {@link Result} 结构的 403。
 * （注：本项目大多数角色 / 归属校验在 Service 层经 AuthUtil 抛 BusinessException 处理，
 *  此处覆盖由 Spring Security 过滤链直接拒绝的少数场景，保证响应体结构一致。）
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.forbidden("无权限访问该资源")));
    }
}
