package com.twentyzhang.bluewhale.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitUtilTest {

    @Mock StringRedisTemplate redis;

    @Test
    void tryAcquire_belowLimit_returnsTrue() {
        // The actual call passes TWO vararg args (limit + windowSeconds), so use any(), any()
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        RateLimitUtil util = new RateLimitUtil(redis);
        assertTrue(util.tryAcquire("rl:u:1", 20, 60));
    }

    @Test
    void tryAcquire_overLimit_returnsFalse() {
        // Lua returns 0 when counter exceeds limit → tryAcquire must return false
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);
        RateLimitUtil util = new RateLimitUtil(redis);
        assertFalse(util.tryAcquire("rl:u:1", 20, 60));
    }
}
