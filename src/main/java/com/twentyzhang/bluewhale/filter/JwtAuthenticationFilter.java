package com.twentyzhang.bluewhale.filter;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.util.JwtUtil;
import com.twentyzhang.bluewhale.util.TokenVersionStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenVersionStore tokenVersionStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (!jwtUtil.isValid(token)) {
            chain.doFilter(request, response);
            return;
        }

        Long userId  = jwtUtil.getUserId(token);

        // 即时撤销校验：令牌版本低于当前版本（退出登录/改密后）视为已失效，不写入认证上下文
        if (!tokenVersionStore.isCurrent(userId, jwtUtil.getTokenVersion(token))) {
            chain.doFilter(request, response);
            return;
        }

        String role  = jwtUtil.getRole(token);
        Long storeId = jwtUtil.getStoreId(token);

        AuthUser authUser = new AuthUser(userId, role, storeId);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        authUser, null, List.of(new SimpleGrantedAuthority(role)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
