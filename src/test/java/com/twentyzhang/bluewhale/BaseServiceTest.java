package com.twentyzhang.bluewhale;

import com.twentyzhang.bluewhale.common.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public abstract class BaseServiceTest {

    /**
     * 将给定用户写入 SecurityContext，模拟已登录状态。
     * 子类测试需要鉴权的方法时调用此辅助方法。
     */
    protected void mockAuthUser(Long userId, String role, Long storeId) {
        AuthUser authUser = new AuthUser(userId, role, storeId);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                authUser, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
