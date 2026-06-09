package com.twentyzhang.bluewhale.interceptor;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP inbound 拦截器：
 *  - CONNECT：用 Authorization: Bearer <jwt> 鉴权，绑定 ChatPrincipal。
 *  - SUBSCRIBE：客服订阅 /topic/store.{id} 须 role=STAFF 且 storeId 匹配（防越权订阅别店）。
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    private static final String STORE_TOPIC_PREFIX = "/topic/store.";

    @Override
    @SuppressWarnings("unchecked")
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Obtain a mutable accessor to set the Principal; if the headers are already
            // immutable (e.g. in tests) we wrap them in a new mutable accessor and
            // rebuild the message so the mutation is always visible downstream.
            if (!accessor.isMutable()) {
                StompHeaderAccessor mutable = StompHeaderAccessor.wrap(message);
                authenticate(mutable);
                return MessageBuilder.createMessage(message.getPayload(), mutable.getMessageHeaders());
            }
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("缺少认证信息");
        }
        String token = header.substring(7);
        if (!jwtUtil.isValid(token)) {
            throw new IllegalArgumentException("Token 无效");
        }
        AuthUser user = new AuthUser(jwtUtil.getUserId(token), jwtUtil.getRole(token), jwtUtil.getStoreId(token));
        accessor.setUser(new ChatPrincipal(user));
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null || !dest.startsWith(STORE_TOPIC_PREFIX)) {
            return; // 仅校验店铺主题；/user/queue/** 由 Spring 按 principal 隔离
        }
        AuthUser user = currentUser(accessor);
        if (!AuthUtil.ROLE_STAFF.equals(user.role())) {
            throw new IllegalArgumentException("仅客服可订阅店铺会话");
        }
        String storeIdStr = dest.substring(STORE_TOPIC_PREFIX.length());
        if (user.storeId() == null || !user.storeId().toString().equals(storeIdStr)) {
            throw new IllegalArgumentException("无权订阅该店铺会话");
        }
    }

    private AuthUser currentUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof ChatPrincipal p) {
            return p.user();
        }
        throw new IllegalArgumentException("未认证");
    }
}
