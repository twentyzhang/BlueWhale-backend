package com.twentyzhang.bluewhale.job;

import com.twentyzhang.bluewhale.config.SearchProperties;
import com.twentyzhang.bluewhale.entity.IndexOutbox;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.mapper.IndexOutboxMapper;
import com.twentyzhang.bluewhale.mapper.ProductCategoryMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.service.EmbeddingClient;
import com.twentyzhang.bluewhale.service.ProductVectorIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayJob")
class OutboxRelayJobTest {

    @Mock IndexOutboxMapper outboxMapper;
    @Mock ProductMapper productMapper;
    @Mock ProductCategoryMapper productCategoryMapper;
    @Mock EmbeddingClient embeddingClient;
    @Mock ProductVectorIndex vectorIndex;

    SearchProperties props = new SearchProperties();
    OutboxRelayJob job;

    @BeforeEach
    void setUp() {
        props.getOutbox().setMaxRetry(5);
        props.getOutbox().setPollBatchSize(50);
        job = new OutboxRelayJob(outboxMapper, productMapper, productCategoryMapper,
                embeddingClient, vectorIndex, props);
    }

    private static IndexOutbox event(long id, long productId, String op, int retry) {
        return IndexOutbox.builder().id(id).productId(productId).op(op)
                .status("PENDING").retryCount(retry).build();
    }

    private static Product product(long id) {
        return Product.builder().id(id).categoryId(100L).name("耳机")
                .price(new BigDecimal("9.90")).storeId(1L).build();
    }

    @Test
    @DisplayName("UPSERT：embed + upsert + markDone")
    void upsert_embedsAndUpserts() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(event(1L, 10L, "UPSERT", 0)));
        when(productMapper.selectById(10L)).thenReturn(product(10L));
        when(productCategoryMapper.selectById(100L)).thenReturn(null);
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.1f});

        job.pollAndProcess();

        verify(embeddingClient).embed("耳机");
        verify(vectorIndex).upsert(eq(10L), any(), any());
        verify(outboxMapper).markDone(1L);
    }

    @Test
    @DisplayName("DELETE：vectorIndex.delete + markDone")
    void delete_removesPoint() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(event(2L, 11L, "DELETE", 0)));

        job.pollAndProcess();

        verify(vectorIndex).delete(11L);
        verify(outboxMapper).markDone(2L);
        verify(embeddingClient, never()).embed(any());
    }

    @Test
    @DisplayName("UPSERT 但商品已不存在 → 退化为 delete")
    void upsert_missingProduct_deletes() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(event(3L, 12L, "UPSERT", 0)));
        when(productMapper.selectById(12L)).thenReturn(null);

        job.pollAndProcess();

        verify(vectorIndex).delete(12L);
        verify(outboxMapper).markDone(3L);
    }

    @Test
    @DisplayName("处理失败且重试未超限 → markRetry")
    void failure_retry() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(event(4L, 13L, "UPSERT", 0)));
        when(productMapper.selectById(13L)).thenReturn(product(13L));
        when(productCategoryMapper.selectById(anyLong())).thenReturn(null);
        when(embeddingClient.embed(anyString())).thenThrow(new RuntimeException("通义超时"));

        job.pollAndProcess();

        verify(outboxMapper).markRetry(eq(4L), anyString());
        verify(outboxMapper, never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("处理失败且重试超限 → markFailed")
    void failure_exhausted_markFailed() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(event(5L, 14L, "UPSERT", 4)));
        when(productMapper.selectById(14L)).thenReturn(product(14L));
        when(productCategoryMapper.selectById(anyLong())).thenReturn(null);
        when(embeddingClient.embed(anyString())).thenThrow(new RuntimeException("通义超时"));

        job.pollAndProcess();

        verify(outboxMapper).markFailed(eq(5L), anyString());
        verify(outboxMapper, never()).markRetry(anyLong(), anyString());
    }

    @Test
    @DisplayName("Qdrant 不可用 → 整批跳过，不动事件")
    void qdrantDown_skipsBatch() {
        when(outboxMapper.selectPending(anyInt())).thenReturn(List.of(event(6L, 15L, "UPSERT", 0)));
        doThrow(new RuntimeException("connect refused")).when(vectorIndex).ensureCollection();

        job.pollAndProcess();

        verify(outboxMapper, never()).markDone(anyLong());
        verify(outboxMapper, never()).markRetry(anyLong(), anyString());
        verify(outboxMapper, never()).markFailed(anyLong(), anyString());
    }
}
