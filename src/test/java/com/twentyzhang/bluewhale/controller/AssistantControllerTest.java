package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.config.AgentProperties;
import com.twentyzhang.bluewhale.service.AssistantAgentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantControllerTest {

    @Mock AssistantAgentService agentService;

    @Test
    void qa_delegatesToService_withSseEmitter() {
        // 直接单元化控制器（不起 Spring）：构造时注入 mock，调用方法验证委派
        AssistantController c = new AssistantController(agentService, new AgentProperties());
        // 模拟 SecurityContext 已设登录用户
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new com.twentyzhang.bluewhale.common.AuthUser(1L, "CUSTOMER", null), null, null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            SseEmitter e = c.chat("耳机", null);
            org.junit.jupiter.api.Assertions.assertNotNull(e);
            verify(agentService).chat(eq("耳机"), argThat(ctx -> ctx.userId().equals(1L)), any(SseEmitter.class));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
