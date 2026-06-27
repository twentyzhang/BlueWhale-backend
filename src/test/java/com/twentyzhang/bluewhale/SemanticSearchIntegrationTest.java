package com.twentyzhang.bluewhale;

import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.job.OutboxRelayJob;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.service.EmbeddingClient;
import com.twentyzhang.bluewhale.service.ProductVectorIndex;
import com.twentyzhang.bluewhale.service.SemanticSearchService;
import com.twentyzhang.bluewhale.entity.IndexOutbox;
import com.twentyzhang.bluewhale.mapper.IndexOutboxMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AI 语义搜索端到端（需本地 Qdrant + MySQL）。embedding 用桩（固定向量），不调真通义。
 * 流程：插商品 → 入队 UPSERT → 跑中继同步 Qdrant → 语义搜索召回；删除 → 中继 → 搜不到。
 */
@SpringBootTest
@DisplayName("AI 语义搜索端到端")
class SemanticSearchIntegrationTest {

    @Autowired ProductMapper productMapper;
    @Autowired IndexOutboxMapper outboxMapper;
    @Autowired OutboxRelayJob relayJob;
    @Autowired SemanticSearchService semanticSearchService;
    @Autowired ProductVectorIndex vectorIndex;

    @MockitoBean EmbeddingClient embeddingClient;   // 桩：固定向量，避免调真通义

    private float[] fixedVector() {
        float[] v = new float[1024];
        v[0] = 1.0f; // 所有文本/查询同向量 → cosine 命中
        return v;
    }

    private boolean qdrantUp() {
        try { vectorIndex.ensureCollection(); return true; }
        catch (Exception e) { return false; }
    }

    @Test
    @DisplayName("插商品→中继→语义搜索召回；删除→中继→搜不到")
    void endToEnd() {
        Assumptions.assumeTrue(qdrantUp(), "本地 Qdrant 不可达，跳过语义搜索集成测试");
        when(embeddingClient.embed(anyString())).thenReturn(fixedVector());

        // 插一个属于种子店铺(1)、种子分类(3 数码)的商品
        Product p = Product.builder().storeId(1L).categoryId(3L)
                .name("语义测试耳机_" + System.currentTimeMillis())
                .price(new BigDecimal("9.90")).stock(10).build();
        productMapper.insert(p);
        outboxMapper.insert(IndexOutbox.builder()
                .productId(p.getId()).op("UPSERT").status("PENDING").retryCount(0).build());

        relayJob.pollAndProcess();   // 同步到 Qdrant

        List<?> hits = semanticSearchService.search("耳机", null, null, null, 50);
        assertTrue(hits.stream().anyMatch(r ->
                ((com.twentyzhang.bluewhale.dto.ProductListItemResponse) r).getId().equals(p.getId())),
                "语义搜索应召回刚插入的商品");

        // 删除 → 入队 DELETE → 中继 → 搜不到
        productMapper.deleteById(p.getId());
        outboxMapper.insert(IndexOutbox.builder()
                .productId(p.getId()).op("DELETE").status("PENDING").retryCount(0).build());
        relayJob.pollAndProcess();

        List<?> after = semanticSearchService.search("耳机", null, null, null, 50);
        assertTrue(after.stream().noneMatch(r ->
                ((com.twentyzhang.bluewhale.dto.ProductListItemResponse) r).getId().equals(p.getId())),
                "删除并同步后不应再召回");
    }
}
