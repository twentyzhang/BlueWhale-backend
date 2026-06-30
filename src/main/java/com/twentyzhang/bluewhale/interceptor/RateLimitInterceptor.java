package com.twentyzhang.bluewhale.interceptor;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.util.RateLimitUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** AI 端点限流：登录按 userId、游客按 IP，固定窗口令牌桶。 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitUtil rateLimitUtil;

    @Value("${ratelimit.ai.limit:20}") int limit;
    @Value("${ratelimit.ai.window-seconds:60}") int windowSeconds;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        String subject = currentSubject(req);
        String key = "rl:ai:" + subject;
        if (!rateLimitUtil.tryAcquire(key, limit, windowSeconds)) {
            throw new BusinessException(429, "请求太频繁，请稍后再试");
        }
        return true;
    }

    private String currentSubject(HttpServletRequest req) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AuthUser u) return "u:" + u.userId();
        String xff = req.getHeader("X-Forwarded-For");
        return "ip:" + (xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : req.getRemoteAddr());
    }
}
