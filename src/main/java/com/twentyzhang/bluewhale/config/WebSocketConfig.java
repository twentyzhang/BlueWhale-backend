package com.twentyzhang.bluewhale.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 纯 WebSocket 端点（前端为独立项目，暂不加 SockJS 回退）
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 内存 SimpleBroker：主题广播 /topic、用户私信 /queue
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端 SEND 目的地前缀
        registry.setApplicationDestinationPrefixes("/app");
        // user-destination 前缀（convertAndSendToUser 解析）
        registry.setUserDestinationPrefix("/user");
    }
}
