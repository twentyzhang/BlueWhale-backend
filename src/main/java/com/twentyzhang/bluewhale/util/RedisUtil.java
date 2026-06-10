package com.twentyzhang.bluewhale.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

    /** deleteByPrefix 单批删除上限，避免一次删除过多 key 阻塞。 */
    private static final int DELETE_BATCH_SIZE = 500;

    /**
     * 存入字符串值（永不过期）
     */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 存入字符串值并设置过期时间
     */
    public void setWithExpire(String key, String value, long timeout, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 获取字符串值，key 不存在时返回 null
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 删除指定 key，返回是否删除成功
     */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /**
     * 判断 key 是否存在
     */
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /**
     * 设置 key 的过期时间
     */
    public Boolean expire(String key, long timeout, TimeUnit timeUnit) {
        return stringRedisTemplate.expire(key, timeout, timeUnit);
    }

    /**
     * 获取 key 的剩余过期时间（秒），key 不存在返回 -2，永不过期返回 -1
     */
    public Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    // ---------- Set 操作（在线状态） ----------

    /** 向集合添加成员，返回新增成员数。 */
    public Long sAdd(String key, String... values) {
        return stringRedisTemplate.opsForSet().add(key, values);
    }

    /** 从集合移除成员，返回移除成员数。 */
    public Long sRemove(String key, Object... values) {
        return stringRedisTemplate.opsForSet().remove(key, values);
    }

    /** 返回集合元素个数（key 不存在返回 0）。 */
    public Long sCard(String key) {
        Long size = stringRedisTemplate.opsForSet().size(key);
        return size != null ? size : 0L;
    }

    /** 判断成员是否在集合中。 */
    public Boolean sIsMember(String key, Object value) {
        return stringRedisTemplate.opsForSet().isMember(key, value);
    }

    /**
     * 按前缀批量删除 key（用于缓存失效，如清除某商品的全部分页评论缓存）。
     * <p>使用 SCAN 游标分批扫描 + 批量删除，<b>不使用 KEYS</b>（KEYS 会一次性遍历整库、阻塞 Redis）。
     *
     * @param prefix key 前缀，内部匹配 {@code prefix*}
     */
    public void deleteByPrefix(String prefix) {
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(100).build();
        List<String> batch = new ArrayList<>(DELETE_BATCH_SIZE);
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= DELETE_BATCH_SIZE) {
                    stringRedisTemplate.delete(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            stringRedisTemplate.delete(batch);
        }
    }

    // ---------- ZSet 操作（推荐相似度缓存） ----------

    /** 向有序集合添加成员（member → score）。 */
    public Boolean zAdd(String key, String member, double score) {
        return stringRedisTemplate.opsForZSet().add(key, member, score);
    }

    /**
     * 按 score 降序取区间成员（含两端），用于查 Top-N 相似商品。
     * key 不存在或为空时返回空列表（保留 score 降序）。
     */
    public List<String> zRevRange(String key, long start, long end) {
        Set<String> members = stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
        return members == null ? new ArrayList<>() : new ArrayList<>(members);
    }
}
