package com.twentyzhang.bluewhale.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 令牌版本（token version / epoch）存储，支撑 access token 的「即时撤销」。
 *
 * <p>每个用户在 Redis 中维护一个版本号 {@code auth:tokenver:{userId}}：
 * <ul>
 *   <li>签发 access token 时把当前版本写入 {@code ver} 声明；</li>
 *   <li>过滤器校验 token 的 {@code ver} 不小于当前版本，否则视为已撤销；</li>
 *   <li>退出登录 / 改密时 {@link #bump} 自增版本号 → 该用户所有旧 token 立即失效。</li>
 * </ul>
 *
 * <p>键无 TTL（持久），按用户数量有界；版本比较用 {@code >=}，即便键缺失（默认 0）也不会误杀有效 token。
 * 适配项目「每用户一个 refreshToken」的单会话模型：一次撤销使该用户全部令牌失效。
 */
@Component
@RequiredArgsConstructor
public class TokenVersionStore {

    private static final String KEY_PREFIX = "auth:tokenver:";

    private final RedisUtil redisUtil;

    /** 当前版本，键缺失（从未撤销）返回 0。 */
    public long current(Long userId) {
        String v = redisUtil.get(KEY_PREFIX + userId);
        return v == null ? 0L : Long.parseLong(v);
    }

    /** 自增版本并返回新值（撤销该用户全部已签发 token）。 */
    public long bump(Long userId) {
        Long v = redisUtil.increment(KEY_PREFIX + userId);
        return v == null ? 0L : v;
    }

    /** token 携带的版本是否仍有效（不小于当前版本）。 */
    public boolean isCurrent(Long userId, long tokenVersion) {
        return tokenVersion >= current(userId);
    }
}
