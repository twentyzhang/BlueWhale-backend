package com.twentyzhang.bluewhale.common;

import java.security.Principal;

/**
 * STOMP 连接的已认证主体。
 * getName() 返回 userId 字符串：Spring user-destination（convertAndSendToUser）按此名投递。
 * 同时携带完整 AuthUser，供拦截器做 SUBSCRIBE/SEND 授权。
 */
public record ChatPrincipal(AuthUser user) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(user.userId());
    }
}
