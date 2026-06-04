package com.twentyzhang.bluewhale.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p>缓存值用 Jackson JSON 序列化（注入 Spring 容器的 {@code ObjectMapper}，已含 JavaTimeModule，能处理
 * {@code LocalDateTime} 等时间类型）。
 *
 * <ul>
 *   <li><b>穿透防护</b>：DB 查询为 null 时写入空值占位 {@link #NULL_SENTINEL}（固定 60s），
 *       后续读到占位直接抛 {@code BusinessException(404)}，避免不存在的 key 反复打 DB。</li>
 *   <li><b>雪崩防护</b>：实际 TTL = {@code baseTtl + random(randomRange)}，打散过期时间。</li>
 *   <li><b>容错</b>：缓存反序列化失败（如 DTO 结构变更）视为未命中，回源 DB，不让脏缓存阻断请求。</li>
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

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    /**
     * 读取单实体（如商品/商店详情）：命中即返回；未命中回源；DB 为 null 则写空值占位并抛 404。
     *
     * @param key         缓存 key
     * @param baseTtl     基础过期秒数
     * @param randomRange TTL 随机抖动上限秒数（防雪崩，传 0 表示不抖动）
     * @param loader      回源逻辑（查 DB），返回 null 表示资源不存在
     * @param clazz       反序列化目标类型
     */
    public <T> T getOrLoad(String key, long baseTtl, long randomRange, Supplier<T> loader, Class<T> clazz) {
        String cached = redisUtil.get(key);
        if (cached != null) {
            if (NULL_SENTINEL.equals(cached)) {
                throw new BusinessException(Result.CODE_NOT_FOUND, "资源不存在");
            }
            try {
                return objectMapper.readValue(cached, clazz);
            } catch (Exception e) {
                log.warn("缓存反序列化失败，回源 DB：key={}, err={}", key, e.getMessage());
            }
        }
        T value = loader.get();
        if (value == null) {
            redisUtil.setWithExpire(key, NULL_SENTINEL, NULL_TTL_SECONDS, TimeUnit.SECONDS);
            throw new BusinessException(Result.CODE_NOT_FOUND, "资源不存在");
        }
        redisUtil.setWithExpire(key, serialize(value), randomTtl(baseTtl, randomRange), TimeUnit.SECONDS);
        return value;
    }

    /**
     * 读取集合 / 分页等泛型结果（如 {@code List<X>}、{@code IPage<X>}）：语义同上，
     * 但<b>不做空值 404 占位</b>（空列表是合法结果而非"不存在"）；loader 返回 null 时不缓存、直接返回 null。
     *
     * @param typeRef 携带泛型信息的反序列化目标（如 {@code new TypeReference<Page<ReviewResponse>>(){}}）
     */
    public <T> T getOrLoad(String key, long baseTtl, long randomRange, Supplier<T> loader, TypeReference<T> typeRef) {
        String cached = redisUtil.get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, typeRef);
            } catch (Exception e) {
                log.warn("缓存反序列化失败，回源 DB：key={}, err={}", key, e.getMessage());
            }
        }
        T value = loader.get();
        if (value != null) {
            redisUtil.setWithExpire(key, serialize(value), randomTtl(baseTtl, randomRange), TimeUnit.SECONDS);
        }
        return value;
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
