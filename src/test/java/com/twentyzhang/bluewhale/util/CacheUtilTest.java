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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

        // 第二次命中（首次 load 已回填 L1，直接命中本地缓存，不再回源、不再读 Redis）
        Page<Dto> hit = cacheUtil.getOrLoad("page", 600, 0,
                () -> { throw new AssertionError("不应回源"); }, new TypeReference<Page<Dto>>() {});

        assertEquals(25, hit.getTotal());
        assertEquals(2, hit.getCurrent());
        assertEquals(1, hit.getRecords().size());
        assertEquals("甲", hit.getRecords().get(0).name());
    }

    @Test
    @DisplayName("single-flight：并发未命中同一 key，loader 仅执行一次")
    void singleFlight_concurrentMissesLoadOnce() throws Exception {
        when(redisUtil.get("hot")).thenReturn(null);
        AtomicInteger loaderCalls = new AtomicInteger();
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Dto>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return cacheUtil.getOrLoad("hot", 300, 0, () -> {
                    loaderCalls.incrementAndGet();
                    try { Thread.sleep(50); } catch (InterruptedException ignored) { }
                    return new Dto(1L, "A", null);
                }, Dto.class);
            }));
        }
        start.countDown();   // 12 线程同时冲
        for (Future<Dto> f : futures) {
            assertEquals(new Dto(1L, "A", null), f.get());
        }
        pool.shutdown();
        assertEquals(1, loaderCalls.get(), "single-flight 应只回源一次");
    }

    @Test
    @DisplayName("invalidate：清 L1+L2，失效后下次读重新回源")
    void invalidate_clearsBothLayersAndReloads() {
        when(redisUtil.get("k")).thenReturn(null);   // Redis 始终未命中
        AtomicInteger loaderCalls = new AtomicInteger();

        // 第一次：未命中 → 回源(count=1) → 写两级；若不失效，第二次会命中 L1
        cacheUtil.getOrLoad("k", 300, 0,
                () -> { loaderCalls.incrementAndGet(); return new Dto(1L, "A", null); }, Dto.class);

        cacheUtil.invalidate("k");
        verify(redisUtil).delete("k");

        // 第二次：L1 已清、Redis 仍未命中 → 再次回源(count=2)
        cacheUtil.getOrLoad("k", 300, 0,
                () -> { loaderCalls.incrementAndGet(); return new Dto(1L, "A", null); }, Dto.class);

        assertEquals(2, loaderCalls.get(), "失效后应再次回源");
    }
}
