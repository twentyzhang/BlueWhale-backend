package com.twentyzhang.bluewhale;

import com.twentyzhang.bluewhale.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisTest {

    @Autowired
    private RedisUtil redisUtil;

    private static final String TEST_KEY         = "test:redis:hello";
    private static final String TEST_KEY_EXPIRE  = "test:redis:expire";

    /** 每个测试结束后清理写入的 key，避免污染后续测试 */
    @AfterEach
    void cleanup() {
        redisUtil.delete(TEST_KEY);
        redisUtil.delete(TEST_KEY_EXPIRE);
    }

    @Test
    void testSetAndGet() {
        String value = "bluewhale";

        redisUtil.set(TEST_KEY, value);
        String result = redisUtil.get(TEST_KEY);

        assertEquals(value, result, "写入的值应与读取的值相同");
    }

    @Test
    void testHasKey() {
        assertFalse(redisUtil.hasKey(TEST_KEY), "写入前 key 不应存在");

        redisUtil.set(TEST_KEY, "exists");
        assertTrue(redisUtil.hasKey(TEST_KEY), "写入后 key 应存在");
    }

    @Test
    void testDelete() {
        redisUtil.set(TEST_KEY, "to-be-deleted");
        assertTrue(redisUtil.hasKey(TEST_KEY));

        redisUtil.delete(TEST_KEY);
        assertFalse(redisUtil.hasKey(TEST_KEY), "删除后 key 不应存在");
        assertNull(redisUtil.get(TEST_KEY), "删除后 get 应返回 null");
    }

    @Test
    void testSetWithExpire() throws InterruptedException {
        redisUtil.setWithExpire(TEST_KEY_EXPIRE, "temp", 2, TimeUnit.SECONDS);

        assertTrue(redisUtil.hasKey(TEST_KEY_EXPIRE), "过期前 key 应存在");
        assertEquals("temp", redisUtil.get(TEST_KEY_EXPIRE));

        // 等待过期
        Thread.sleep(2500);

        assertFalse(redisUtil.hasKey(TEST_KEY_EXPIRE), "过期后 key 不应存在");
        assertNull(redisUtil.get(TEST_KEY_EXPIRE), "过期后 get 应返回 null");
    }

    @Test
    void testGetExpire() {
        redisUtil.setWithExpire(TEST_KEY_EXPIRE, "ttl-test", 60, TimeUnit.SECONDS);

        Long ttl = redisUtil.getExpire(TEST_KEY_EXPIRE);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 60, "TTL 应在 (0, 60] 秒之间");
    }
}
