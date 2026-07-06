package com.twentyzhang.bluewhale.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class MetricsTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Metrics-Token";
    private static final String PROMETHEUS_PATH = "/actuator/prometheus";
    private static final String ADMIN_AUTHORITY = "ADMIN";

    private final String metricsToken;

    public MetricsTokenFilter(@Value("${management.metrics-token:}") String metricsToken) {
        this.metricsToken = metricsToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isPrometheusRequest(request)
                && StringUtils.hasText(metricsToken)
                && metricsToken.equals(request.getHeader(HEADER_NAME))
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            "metrics-token",
                            null,
                            List.of(new SimpleGrantedAuthority(ADMIN_AUTHORITY)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPrometheusRequest(HttpServletRequest request) {
        return PROMETHEUS_PATH.equals(request.getRequestURI());
    }
}
