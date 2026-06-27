package com.twentyzhang.bluewhale.config;

import com.twentyzhang.bluewhale.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(restAuthenticationEntryPoint)  // 未认证 → 401
                .accessDeniedHandler(restAccessDeniedHandler))            // 已认证无权限 → 403
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                // 商品浏览相关接口对游客开放（无需登录），仅放开下列 GET 路径，
                // 不影响同前缀下的写操作及 /orders、/reports、/admin 等需鉴权接口
                .requestMatchers(HttpMethod.GET,
                        "/api/categories",          // 分类树
                        "/api/stores",              // 商店列表
                        "/api/stores/*",            // 商店详情
                        "/api/stores/*/products",   // 店内商品列表
                        "/api/products",            // 商品搜索
                        "/api/products/*",          // 商品详情
                        "/api/products/*/reviews",  // 商品评论列表
                        "/api/products/*/recommendations", // 商品相关推荐（任务8，开放）
                        "/api/products/semantic"           // AI 语义搜索（开放）
                ).permitAll()
                .requestMatchers("/ws/**").permitAll()   // 握手放行，真正鉴权在 STOMP CONNECT 帧
                .requestMatchers("/api/payments/notify").permitAll()   // 支付回调 webhook（靠 HMAC 验签）
                .requestMatchers("/api/mock-pay/**").permitAll()       // 模拟收银台（外部网关流量）
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
