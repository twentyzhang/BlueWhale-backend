package com.twentyzhang.bluewhale.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@DisplayName("RedisUtil.deleteByPrefix")
@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private Cursor<String> cursor;

    @InjectMocks
    private RedisUtil redisUtil;

    @Test
    @DisplayName("用 SCAN 游标扫描匹配 key 并批量删除")
    void deleteByPrefix_scansAndDeletes() {
        when(stringRedisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("product:reviews:5:1:10", "product:reviews:5:2:10");

        redisUtil.deleteByPrefix("product:reviews:5:");

        // 扫描到的两个 key 被批量删除
        verify(stringRedisTemplate).delete(List.of("product:reviews:5:1:10", "product:reviews:5:2:10"));
        verify(cursor).close();
        // 未使用 keys()（避免阻塞）
        verify(stringRedisTemplate, never()).keys(any());
    }

    @Test
    @DisplayName("无匹配 key 时不调用 delete")
    void deleteByPrefix_noMatch_noDelete() {
        when(stringRedisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);

        redisUtil.deleteByPrefix("nope:");

        verify(stringRedisTemplate, never()).delete(anyCollection());
    }

    @Test
    @DisplayName("sAdd 调用 opsForSet().add")
    void sAdd_delegatesToSetOps() {
        org.springframework.data.redis.core.SetOperations<String, String> setOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add("k", "v")).thenReturn(1L);

        assertEquals(1L, redisUtil.sAdd("k", "v"));
        verify(setOps).add("k", "v");
    }

    @Test
    @DisplayName("sCard 返回集合大小")
    void sCard_returnsSize() {
        org.springframework.data.redis.core.SetOperations<String, String> setOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size("k")).thenReturn(3L);

        assertEquals(3L, redisUtil.sCard("k"));
    }

    @Test
    @DisplayName("sIsMember 委托 opsForSet().isMember")
    void sIsMember_delegates() {
        org.springframework.data.redis.core.SetOperations<String, String> setOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("k", "v")).thenReturn(true);

        assertTrue(redisUtil.sIsMember("k", "v"));
    }
}
