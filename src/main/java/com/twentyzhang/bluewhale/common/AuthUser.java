package com.twentyzhang.bluewhale.common;

/**
 * 存放在 SecurityContext 中的已认证用户信息。
 * 通过 SecurityContextHolder 取出后可直接使用，无需再查数据库。
 */
public record AuthUser(Long userId, String role, Long storeId) {
}
