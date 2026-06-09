package com.twentyzhang.bluewhale.interceptor;

import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StompAuthChannelInterceptor")
class StompAuthChannelInterceptorTest {

    @Mock private JwtUtil jwtUtil;
    @InjectMocks private StompAuthChannelInterceptor interceptor;

    private Message<byte[]> frame(StompCommand command, String authHeader, String destination, ChatPrincipal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authHeader != null) accessor.setNativeHeader("Authorization", authHeader);
        if (destination != null) accessor.setDestination(destination);
        if (user != null) accessor.setUser(user);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("CONNECT 合法 token：绑定 ChatPrincipal")
    void connect_validToken_bindsPrincipal() {
        when(jwtUtil.isValid("good")).thenReturn(true);
        when(jwtUtil.getUserId("good")).thenReturn(9L);
        when(jwtUtil.getRole("good")).thenReturn(AuthUtil.ROLE_STAFF);
        when(jwtUtil.getStoreId("good")).thenReturn(5L);

        Message<?> out = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), null);

        StompHeaderAccessor acc = StompHeaderAccessor.wrap(out);
        assertInstanceOf(ChatPrincipal.class, acc.getUser());
        assertEquals("9", acc.getUser().getName());
    }

    @Test
    @DisplayName("CONNECT 无 token：拒绝")
    void connect_missingToken_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), null));
    }

    @Test
    @DisplayName("CONNECT 非法 token：拒绝")
    void connect_invalidToken_rejected() {
        when(jwtUtil.isValid("bad")).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer bad", null, null), null));
    }

    @Test
    @DisplayName("SUBSCRIBE 越权订阅别店主题：拒绝")
    void subscribe_wrongStore_rejected() {
        ChatPrincipal staff = new ChatPrincipal(new com.twentyzhang.bluewhale.common.AuthUser(9L, AuthUtil.ROLE_STAFF, 5L));
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/store.999", staff), null));
    }

    @Test
    @DisplayName("SUBSCRIBE 本店主题：放行")
    void subscribe_ownStore_ok() {
        ChatPrincipal staff = new ChatPrincipal(new com.twentyzhang.bluewhale.common.AuthUser(9L, AuthUtil.ROLE_STAFF, 5L));
        assertDoesNotThrow(
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/store.5", staff), null));
    }

    @Test
    @DisplayName("SUBSCRIBE 买家订阅店铺主题：拒绝")
    void subscribe_customerToStoreTopic_rejected() {
        ChatPrincipal cust = new ChatPrincipal(new com.twentyzhang.bluewhale.common.AuthUser(1L, AuthUtil.ROLE_CUSTOMER, null));
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/store.5", cust), null));
    }
}
