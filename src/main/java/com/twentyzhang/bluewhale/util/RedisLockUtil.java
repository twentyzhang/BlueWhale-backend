package com.twentyzhang.bluewhale.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的简单分布式锁工具。
 *
 * <p>加锁用 {@code SET key value NX EX}（不存在才设置 + 过期时间），保证互斥与防死锁；
 * 解锁用 Lua 脚本「value 一致才删除」，避免误删其他线程在锁过期后重新持有的锁。
 *
 * <p>注意：这是不可重入、无自动续期的轻量实现，适用于临界区短、可容忍偶发失败重试的场景
 * （如优惠券领取）。需要可重入/看门狗续期请改用 Redisson。
 */
@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    private final StringRedisTemplate stringRedisTemplate;

    /** 释放锁的 Lua 脚本：仅当 value 与持有者一致时才 del，保证「判断 + 删除」原子执行。 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then\n" +
            "    return redis.call('del', KEYS[1])\n" +
            "else\n" +
            "    return 0\n" +
            "end",
            Long.class);

    /**
     * 尝试获取锁：{@code SET lockKey lockValue NX EX expireSeconds}。
     *
     * @param lockKey       锁的 key
     * @param lockValue     持有者标识（建议用 UUID，解锁时据此判断归属，防误删）
     * @param expireSeconds 锁自动过期秒数（防持有者宕机导致死锁）
     * @return true 获取成功；false 已被他人持有
     */
    public boolean tryLock(String lockKey, String lockValue, long expireSeconds) {
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁：通过 Lua 脚本保证「value 一致才删除」的原子性，防止误删其他线程的锁。
     *
     * @return true 确为本线程持有并成功释放；false 锁已不存在或已属于他人（不可误删）
     */
    public boolean unlock(String lockKey, String lockValue) {
        Long result = stringRedisTemplate.execute(
                UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
        return Long.valueOf(1L).equals(result);
    }
}
