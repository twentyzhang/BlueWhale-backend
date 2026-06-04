package com.twentyzhang.bluewhale.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("RedisLockUtil")
@ExtendWith(MockitoExtension.class)
class RedisLockUtilTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RedisLockUtil redisLockUtil;

    @Test
    @DisplayName("tryLock：SET NX EX 成功返回 true")
    void tryLock_success() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("k", "v", 5L, TimeUnit.SECONDS)).thenReturn(true);

        assertTrue(redisLockUtil.tryLock("k", "v", 5));
    }

    @Test
    @DisplayName("tryLock：键已存在（setIfAbsent 返回 false/null）返回 false")
    void tryLock_failsWhenHeld() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(null);

        assertFalse(redisLockUtil.tryLock("k", "v", 5));
    }

    @Test
    @DisplayName("unlock：Lua 脚本返回 1（value 一致）表示成功释放")
    void unlock_success() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        assertTrue(redisLockUtil.unlock("k", "v"));
    }

    @Test
    @DisplayName("unlock：Lua 脚本返回 0（value 不一致/已过期）表示未释放，不误删")
    void unlock_notOwner() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);

        assertFalse(redisLockUtil.unlock("k", "v"));
    }
}
