package com.twentyzhang.bluewhale.listener;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.ChatKeys;
import com.twentyzhang.bluewhale.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/** 连接/断开事件 → 维护 Redis 在线集合。 */
@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private final RedisUtil redisUtil;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        AuthUser user = extractUser(event.getMessage());
        if (user == null) return;
        if (AuthUtil.ROLE_STAFF.equals(user.role()) && user.storeId() != null) {
            redisUtil.sAdd(ChatKeys.onlineStoreStaff(user.storeId()), String.valueOf(user.userId()));
        } else if (AuthUtil.ROLE_CUSTOMER.equals(user.role())) {
            redisUtil.sAdd(ChatKeys.ONLINE_CUSTOMERS, String.valueOf(user.userId()));
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        AuthUser user = extractUser(event.getMessage());
        if (user == null) return;
        if (AuthUtil.ROLE_STAFF.equals(user.role()) && user.storeId() != null) {
            redisUtil.sRemove(ChatKeys.onlineStoreStaff(user.storeId()), String.valueOf(user.userId()));
        } else if (AuthUtil.ROLE_CUSTOMER.equals(user.role())) {
            redisUtil.sRemove(ChatKeys.ONLINE_CUSTOMERS, String.valueOf(user.userId()));
        }
    }

    private AuthUser extractUser(org.springframework.messaging.Message<byte[]> message) {
        Principal principal = StompHeaderAccessor.wrap(message).getUser();
        return (principal instanceof ChatPrincipal p) ? p.user() : null;
    }
}
