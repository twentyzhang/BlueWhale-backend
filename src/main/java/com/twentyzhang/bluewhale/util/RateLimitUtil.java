package com.twentyzhang.bluewhale.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** Redis 固定窗口计数限流（与 RedisLockUtil 同源、Lua 原子）。 */
@Component
public class RateLimitUtil {

    // 第一次访问 SET key 1 并设 TTL；之后 INCR；返回 1=放行，0=超限
    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local c = redis.call('incr', KEYS[1])
            if tonumber(c) == 1 then redis.call('expire', KEYS[1], ARGV[2]) end
            if tonumber(c) > tonumber(ARGV[1]) then return 0 else return 1 end
            """, Long.class);

    private final StringRedisTemplate redis;

    public RateLimitUtil(StringRedisTemplate redis) { this.redis = redis; }

    /** 在 windowSeconds 窗口内允许 limit 次；放行返回 true。 */
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        Long ok = redis.execute(SCRIPT, List.of(key),
                String.valueOf(limit), String.valueOf(windowSeconds));
        return ok != null && ok == 1L;
    }
}
