package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.config.SearchProperties;
import com.twentyzhang.bluewhale.service.impl.TongyiEmbeddingClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 轻量纯单元测试：验证 embedding fallback 在熔断/重试耗尽后返回 float[0]，
 * 使 QdrantProductVectorIndex.search() 因维度不符抛出 RestClientResponseException，
 * 进而被 SemanticSearchServiceImpl.search() 的 catch 块接住触发关键词降级。
 * 不依赖 Spring 上下文，不需要外部服务。
 */
class ResilienceConfigTest {

    /**
     * 构造一个使用默认值的 SearchProperties stub（URL 等字段无需真实，fallback 方法不发网络请求）。
     */
    private static SearchProperties stubProps() {
        return new SearchProperties();
    }

    @Test
    void embedFallback_returnsEmptyArray() {
        TongyiEmbeddingClient client = new TongyiEmbeddingClient(stubProps());

        float[] result = client.embedFallback("some query text", new RuntimeException("embedding service down"));

        assertNotNull(result, "fallback 不应返回 null");
        assertEquals(0, result.length, "fallback 应返回空数组 float[0] 以触发 Qdrant 维度不符异常 → 关键词降级");
    }

    @Test
    void embedFallback_withDifferentExceptionTypes_returnsEmptyArray() {
        TongyiEmbeddingClient client = new TongyiEmbeddingClient(stubProps());

        float[] onTimeout = client.embedFallback("query", new java.net.SocketTimeoutException("read timeout"));
        float[] onIllegalState = client.embedFallback("query", new IllegalStateException("通义 embedding 返回为空"));

        assertEquals(0, onTimeout.length, "超时场景 fallback 应返回 float[0]");
        assertEquals(0, onIllegalState.length, "空响应场景 fallback 应返回 float[0]");
    }
}
