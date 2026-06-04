package com.twentyzhang.bluewhale.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.twentyzhang.bluewhale.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CacheUtil")
@ExtendWith(MockitoExtension.class)
class CacheUtilTest {

    @Mock private RedisUtil redisUtil;

    private CacheUtil cacheUtil;

    /** 仿 Spring 容器的 ObjectMapper：注册 jsr310、忽略未知字段。 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 可序列化的测试 DTO（record，含 LocalDateTime 验证 jsr310）。 */
    record Dto(Long id, String name, LocalDateTime time) {}

    @BeforeEach
    void setUp() {
        cacheUtil = new CacheUtil(redisUtil, objectMapper);
    }

    @Test
    @DisplayName("未命中：执行 loader、写缓存、返回结果；TTL 落在 [base, base+range)")
    void miss_loadsAndCaches() {
        when(redisUtil.get("k")).thenReturn(null);
        Dto loaded = new Dto(1L, "A", LocalDateTime.now());

        Dto result = cacheUtil.getOrLoad("k", 300, 60, () -> loaded, Dto.class);

        assertEquals(loaded, result);
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(redisUtil).setWithExpire(eq("k"), anyString(), ttl.capture(), eq(TimeUnit.SECONDS));
        assertTrue(ttl.getValue() >= 300 && ttl.getValue() < 360, "TTL 应在 [300,360)，实际 " + ttl.getValue());
    }

    @Test
    @DisplayName("命中：反序列化缓存值返回，不执行 loader")
    void hit_returnsDeserializedWithoutLoader() throws Exception {
        Dto cached = new Dto(7L, "缓存", LocalDateTime.of(2026, 6, 4, 10, 0));
        when(redisUtil.get("k")).thenReturn(objectMapper.writeValueAsString(cached));
        AtomicInteger loaderCalls = new AtomicInteger();

        Dto result = cacheUtil.getOrLoad("k", 300, 60, () -> { loaderCalls.incrementAndGet(); return null; }, Dto.class);

        assertEquals(cached, result);
        assertEquals(0, loaderCalls.get(), "命中时不应回源");
        verify(redisUtil, never()).setWithExpire(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("穿透防护：loader 返回 null → 写 CACHE_NULL(60s) 并抛 404")
    void loaderNull_setsSentinelAndThrows404() {
        when(redisUtil.get("k")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cacheUtil.getOrLoad("k", 300, 60, () -> null, Dto.class));

        assertEquals(404, ex.getCode());
        verify(redisUtil).setWithExpire("k", CacheUtil.NULL_SENTINEL, 60, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("穿透防护：命中 CACHE_NULL 占位 → 直接抛 404，不回源")
    void hitSentinel_throws404() {
        when(redisUtil.get("k")).thenReturn(CacheUtil.NULL_SENTINEL);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cacheUtil.getOrLoad("k", 300, 60, () -> new Dto(1L, "x", null), Dto.class));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("泛型重载：IPage<Dto>（Page）可 JSON 往返")
    void typeRef_pageRoundTrip() {
        Page<Dto> page = new Page<>(2, 10);
        page.setTotal(25);
        page.setRecords(List.of(new Dto(1L, "甲", LocalDateTime.of(2026, 1, 1, 0, 0))));

        // 第一次未命中：捕获写入的 JSON
        when(redisUtil.get("page")).thenReturn(null);
        Page<Dto> first = cacheUtil.getOrLoad("page", 600, 0,
                () -> page, new TypeReference<Page<Dto>>() {});
        assertEquals(25, first.getTotal());

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redisUtil).setWithExpire(eq("page"), json.capture(), anyLong(), any());

        // 模拟第二次命中：用上次写入的 JSON 反序列化
        when(redisUtil.get("page")).thenReturn(json.getValue());
        Page<Dto> hit = cacheUtil.getOrLoad("page", 600, 0,
                () -> { throw new AssertionError("不应回源"); }, new TypeReference<Page<Dto>>() {});

        assertEquals(25, hit.getTotal());
        assertEquals(2, hit.getCurrent());
        assertEquals(1, hit.getRecords().size());
        assertEquals("甲", hit.getRecords().get(0).name());
    }
}
