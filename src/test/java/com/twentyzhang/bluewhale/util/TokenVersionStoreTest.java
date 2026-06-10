package com.twentyzhang.bluewhale.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TokenVersionStore")
@ExtendWith(MockitoExtension.class)
class TokenVersionStoreTest {

    @Mock private RedisUtil redisUtil;

    @InjectMocks private TokenVersionStore store;

    @Test
    @DisplayName("current：键缺失（从未撤销）返回 0")
    void current_absentReturnsZero() {
        when(redisUtil.get("auth:tokenver:1")).thenReturn(null);
        assertEquals(0L, store.current(1L));
    }

    @Test
    @DisplayName("current：解析已存版本号")
    void current_parsesStored() {
        when(redisUtil.get("auth:tokenver:1")).thenReturn("3");
        assertEquals(3L, store.current(1L));
    }

    @Test
    @DisplayName("bump：自增并返回新版本")
    void bump_incrementsAndReturns() {
        when(redisUtil.increment("auth:tokenver:1")).thenReturn(4L);
        assertEquals(4L, store.bump(1L));
    }

    @Test
    @DisplayName("isCurrent：token 版本 >= 当前为有效，< 当前为已撤销")
    void isCurrent_comparesVersions() {
        when(redisUtil.get("auth:tokenver:1")).thenReturn("2");
        assertTrue(store.isCurrent(1L, 2L));   // 等于当前 → 有效
        assertTrue(store.isCurrent(1L, 3L));   // 高于当前 → 有效
        assertFalse(store.isCurrent(1L, 1L));  // 低于当前 → 已撤销
    }
}
