package com.twentyzhang.bluewhale.config;

import com.twentyzhang.bluewhale.interceptor.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 纯 WebSocket 端点（前端为独立项目，暂不加 SockJS 回退）
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 内存 SimpleBroker：主题广播 /topic、用户私信 /queue
        // 启用 STOMP 心跳（10s 收发，B3）：探测死连接 → 关闭 → 触发 DISCONNECT → 在线状态自动清理。
        // 不支持心跳的客户端协商为 0、不受影响；需配 TaskScheduler。
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(webSocketHeartbeatScheduler());
        // 客户端 SEND 目的地前缀
        registry.setApplicationDestinationPrefixes("/app");
        // user-destination 前缀（convertAndSendToUser 解析）
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    /** STOMP 心跳调度器（SimpleBroker 启用心跳时必需）。 */
    @Bean
    public TaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 自定义 STOMP 错误处理器（B2）：失败时回结构化 ERROR 帧。
     * 声明为 Bean 即被 WebSocketMessageBrokerConfigurationSupport 自动织入 StompSubProtocolHandler。
     */
    @Bean
    public StompSubProtocolErrorHandler stompSubProtocolErrorHandler() {
        return new ChatStompErrorHandler();
    }
}
