package com.twentyzhang.bluewhale.listener;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.RedisUtil;
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
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketPresenceListener")
class WebSocketPresenceListenerTest {

    @Mock private RedisUtil redisUtil;
    @InjectMocks private WebSocketPresenceListener listener;

    private Message<byte[]> msg(AuthUser user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        if (user != null) accessor.setUser(new ChatPrincipal(user));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("客服上线：加入店铺在线集合")
    void staffConnected_addsToStoreSet() {
        listener.onConnected(new SessionConnectedEvent(this,
                msg(new AuthUser(9L, AuthUtil.ROLE_STAFF, 5L))));
        verify(redisUtil).sAdd("cs:online:store:5", "9");
    }

    @Test
    @DisplayName("买家上线：加入买家在线集合")
    void customerConnected_addsToCustomerSet() {
        listener.onConnected(new SessionConnectedEvent(this,
                msg(new AuthUser(1L, AuthUtil.ROLE_CUSTOMER, null))));
        verify(redisUtil).sAdd("cs:online:customers", "1");
    }

    @Test
    @DisplayName("客服下线：移出店铺在线集合")
    void staffDisconnected_removesFromStoreSet() {
        listener.onDisconnected(new SessionDisconnectEvent(this,
                msg(new AuthUser(9L, AuthUtil.ROLE_STAFF, 5L)), "sess-1", null));
        verify(redisUtil).sRemove("cs:online:store:5", "9");
    }

    @Test
    @DisplayName("买家下线：移出买家在线集合")
    void customerDisconnected_removesFromCustomerSet() {
        listener.onDisconnected(new SessionDisconnectEvent(this,
                msg(new AuthUser(1L, AuthUtil.ROLE_CUSTOMER, null)), "sess-2", null));
        verify(redisUtil).sRemove("cs:online:customers", "1");
    }

    @Test
    @DisplayName("无 Principal 的连接事件：忽略，不触碰 Redis")
    void noPrincipal_ignored() {
        listener.onConnected(new SessionConnectedEvent(this, msg(null)));
        verifyNoInteractions(redisUtil);
    }
}
