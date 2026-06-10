package com.twentyzhang.bluewhale.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 通用读缓存工具：封装「查缓存命中即返回、未命中回源 DB 并写缓存」的 getOrLoad 模式，减少各 Service 重复代码。
 *
 * <p>三级缓存：<b>L1 本地 Caffeine</b> → <b>L2 Redis</b> → <b>DB</b>。读路径 L1 命中省去 Redis 往返与反序列化的网络开销；
 * L2 命中回填 L1。缓存值用 Jackson JSON 序列化（注入容器的 {@code ObjectMapper}，含 JavaTimeModule）。
 *
 * <ul>
 *   <li><b>穿透防护</b>：DB 查询为 null 时写入空值占位 {@link #NULL_SENTINEL}（固定 60s），后续读到占位直接抛 404。</li>
 *   <li><b>雪崩防护</b>：实际 L2 TTL = {@code baseTtl + random(randomRange)}，打散过期时间。</li>
 *   <li><b>击穿防护（single-flight）</b>：未命中时按 key 条带加锁，同一 key 的并发请求只放一个回源、其余等待复用，
 *       避免热点 key 失效瞬间的「并发回源风暴」。</li>
 *   <li><b>容错</b>：缓存反序列化失败（如 DTO 结构变更）视为未命中，回源 DB，不让脏缓存阻断请求。</li>
 *   <li><b>一致性</b>：写操作调 {@link #invalidate}/{@link #invalidateByPrefix} 同时失效 L1+L2；L1 另设短 TTL 兜底。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheUtil {

    /** 缓存穿透防护：DB 查询为空时存入的占位标记。 */
    public static final String NULL_SENTINEL = "CACHE_NULL";
    /** 空值占位固定过期秒数。 */
    private static final long NULL_TTL_SECONDS = 60;

    /** single-flight 锁条带数：同 key 落同条带，未命中时串行回源防击穿。 */
    private static final int LOCK_STRIPES = 64;
    /** L1 本地缓存最大条目数（超出按 LRU 淘汰）。 */
    private static final long L1_MAX_SIZE = 10_000;
    /** L1 写后过期秒数：显式失效之外的兜底，限制 L1 脏数据窗口。 */
    private static final long L1_TTL_SECONDS = 5;

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    /** L1 本地缓存：key → 序列化 JSON（与 Redis 同格式，含空值占位）。 */
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .maximumSize(L1_MAX_SIZE)
            .expireAfterWrite(L1_TTL_SECONDS, TimeUnit.SECONDS)
            .build();

    /** 固定锁条带，按 key 哈希取模，避免为每个 key 创建锁对象、内存无上限。 */
    private final Object[] stripes = createStripes();

    private static Object[] createStripes() {
        Object[] s = new Object[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            s[i] = new Object();
        }
        return s;
    }

    private Object stripeFor(String key) {
        return stripes[(key.hashCode() & Integer.MAX_VALUE) % LOCK_STRIPES];
    }

    /**
     * 读取单实体（如商品/商店详情）：命中即返回；未命中回源；DB 为 null 则写空值占位并抛 404。
     */
    public <T> T getOrLoad(String key, long baseTtl, long randomRange, Supplier<T> loader, Class<T> clazz) {
        T v = decode(readThrough(key), key, clazz);
        if (v != null) {
            return v;
        }
        // 未命中 → single-flight：同 key 串行回源，其余并发请求等待后复用
        synchronized (stripeFor(key)) {
            v = decode(readThrough(key), key, clazz);   // 双重检查：可能已被先入锁的线程回填
            if (v != null) {
                return v;
            }
            T loaded = loader.get();
            if (loaded == null) {
                put(key, NULL_SENTINEL, NULL_TTL_SECONDS);
                throw new BusinessException(Result.CODE_NOT_FOUND, "资源不存在");
            }
            put(key, serialize(loaded), randomTtl(baseTtl, randomRange));
            return loaded;
        }
    }

    /**
     * 读取集合 / 分页等泛型结果：语义同上，但<b>不做空值 404 占位</b>（空列表是合法结果）；
     * loader 返回 null 时不缓存、直接返回 null。
     */
    public <T> T getOrLoad(String key, long baseTtl, long randomRange, Supplier<T> loader, TypeReference<T> typeRef) {
        T v = decode(readThrough(key), key, typeRef);
        if (v != null) {
            return v;
        }
        synchronized (stripeFor(key)) {
            v = decode(readThrough(key), key, typeRef);
            if (v != null) {
                return v;
            }
            T loaded = loader.get();
            if (loaded != null) {
                put(key, serialize(loaded), randomTtl(baseTtl, randomRange));
            }
            return loaded;
        }
    }

    /** 失效单个 key（同时清 L1 + L2）。写操作后调用，保证两级一致。 */
    public void invalidate(String key) {
        redisUtil.delete(key);
        localCache.invalidate(key);
    }

    /**
     * 按前缀失效：清 L2 前缀匹配的 key + 整个 L1。
     * （前缀匹配在 L1 不便，直接清空 L1；L1 条目少、短 TTL，很快重建。）
     */
    public void invalidateByPrefix(String prefix) {
        redisUtil.deleteByPrefix(prefix);
        localCache.invalidateAll();
    }

    // ── 内部：L1→L2 读穿透、回填、解码 ──────────────────────────────────────────

    /** 读 L1，未命中读 L2 并回填 L1。返回原始 JSON（或 null 表示两级均未命中）。 */
    private String readThrough(String key) {
        String v = localCache.getIfPresent(key);
        if (v != null) {
            return v;
        }
        v = redisUtil.get(key);
        if (v != null) {
            localCache.put(key, v);
        }
        return v;
    }

    /** 写 L2（带 TTL）+ 回填 L1。 */
    private void put(String key, String json, long ttlSeconds) {
        redisUtil.setWithExpire(key, json, ttlSeconds, TimeUnit.SECONDS);
        localCache.put(key, json);
    }

    /** 解码单实体：null=未命中；空值占位抛 404；反序列化失败视为未命中（返回 null）。 */
    private <T> T decode(String cached, String key, Class<T> clazz) {
        if (cached == null) {
            return null;
        }
        if (NULL_SENTINEL.equals(cached)) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "资源不存在");
        }
        try {
            return objectMapper.readValue(cached, clazz);
        } catch (Exception e) {
            log.warn("缓存反序列化失败，回源 DB：key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /** 解码集合/分页：null=未命中；反序列化失败视为未命中（无空值占位语义）。 */
    private <T> T decode(String cached, String key, TypeReference<T> typeRef) {
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, typeRef);
        } catch (Exception e) {
            log.warn("缓存反序列化失败，回源 DB：key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /** 实际 TTL = baseTtl + random[0, randomRange)，打散过期时间防雪崩。 */
    private long randomTtl(long baseTtl, long randomRange) {
        return baseTtl + (randomRange > 0 ? new Random().nextLong(randomRange) : 0L);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("缓存序列化失败: " + e.getMessage(), e);
        }
    }
}
