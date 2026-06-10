package com.twentyzhang.bluewhale.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

/**
 * 自定义 STOMP 错误处理器（第二轮 B2）。
 *
 * <p>原先未注册错误处理器，CONNECT/SEND 鉴权或处理失败时连接被拒但客户端收不到结构化 ERROR 帧、可诊断性弱。
 * 注册本处理器（由 {@code WebSocketMessageBrokerConfigurationSupport} 自动织入到 StompSubProtocolHandler）后，
 * 失败会回送一个带友好原因的 STOMP ERROR 帧。
 */
public class ChatStompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
        String reason = (cause.getMessage() != null && !cause.getMessage().isBlank())
                ? cause.getMessage()
                : "请求处理失败";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(reason);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(reason.getBytes(StandardCharsets.UTF_8),
                accessor.getMessageHeaders());
    }
}
