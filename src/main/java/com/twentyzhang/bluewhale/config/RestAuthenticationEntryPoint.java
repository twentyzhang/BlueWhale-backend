package com.twentyzhang.bluewhale.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证（未登录 / token 无效或缺失）访问受保护接口时的入口点。
 * 返回语义化的 401（而非 Spring Security 默认的 403），响应体用统一 {@link Result} 结构。
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.unauthorized("未登录或登录状态已失效，请重新登录")));
    }
}
