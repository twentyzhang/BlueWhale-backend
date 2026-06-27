package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.entity.IndexOutbox;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.ProductCategory;
import com.twentyzhang.bluewhale.mapper.IndexOutboxMapper;
import com.twentyzhang.bluewhale.mapper.ProductCategoryMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.service.impl.SemanticSearchServiceImpl;
import com.twentyzhang.bluewhale.service.vector.ScoredId;
import com.twentyzhang.bluewhale.service.vector.VectorSearchFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticSearchService")
class SemanticSearchServiceTest {

    @Mock EmbeddingClient embeddingClient;
    @Mock ProductVectorIndex vectorIndex;
    @Mock ProductMapper productMapper;
    @Mock ProductCategoryMapper productCategoryMapper;
    @Mock ProductService productService;
    @Mock IndexOutboxMapper indexOutboxMapper;

    @InjectMocks SemanticSearchServiceImpl service;

    private static Product product(long id, long catId, String name) {
        return Product.builder().id(id).categoryId(catId).name(name)
                .price(new BigDecimal("9.90")).stock(5).imageUrl("u").build();
    }

    @Test
    @DisplayName("正常路径：按命中相关性顺序返回映射结果")
    void search_ordersByRelevance() {
        when(embeddingClient.embed("耳机")).thenReturn(new float[]{0.1f, 0.2f});
        when(vectorIndex.search(any(), any(VectorSearchFilter.class), eq(10)))
                .thenReturn(List.of(new ScoredId(2L, 0.9), new ScoredId(1L, 0.5)));
        when(productMapper.selectBatchIds(List.of(2L, 1L)))
                .thenReturn(List.of(product(1L, 100L, "A"), product(2L, 100L, "B")));
        when(productCategoryMapper.selectBatchIds(anyList()))
                .thenReturn(List.of(ProductCategory.builder().id(100L).name("数码").build()));

        List<ProductListItemResponse> out = service.search("耳机", null, null, null, 10);

        assertEquals(2, out.size());
        assertEquals(2L, out.get(0).getId());   // score 高的在前
        assertEquals(1L, out.get(1).getId());
        assertEquals("数码", out.get(0).getCategoryName());
    }

    @Test
    @DisplayName("过滤条件下推 Qdrant")
    void search_pushesDownFilter() {
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.1f});
        when(vectorIndex.search(any(), any(VectorSearchFilter.class), anyInt())).thenReturn(List.of());

        service.search("x", 100L, new BigDecimal("1"), new BigDecimal("50"), 10);

        ArgumentCaptor<VectorSearchFilter> cap = ArgumentCaptor.forClass(VectorSearchFilter.class);
        verify(vectorIndex).search(any(), cap.capture(), eq(10));
        assertEquals(100L, cap.getValue().categoryId());
        assertEquals(new BigDecimal("1"), cap.getValue().minPrice());
        assertEquals(new BigDecimal("50"), cap.getValue().maxPrice());
    }

    @Test
    @DisplayName("embedding 失败 → 降级关键词搜索")
    void search_embeddingFails_fallback() {
        when(embeddingClient.embed(anyString())).thenThrow(new RuntimeException("通义挂了"));
        Page<ProductListItemResponse> page = new Page<>(1, 10);
        page.setRecords(List.of(ProductListItemResponse.builder().id(7L).name("kw").build()));
        when(productService.searchProducts("x", null, null, null, 1, 10)).thenReturn(page);

        List<ProductListItemResponse> out = service.search("x", null, null, null, 10);

        assertEquals(1, out.size());
        assertEquals(7L, out.get(0).getId());
        verify(productService).searchProducts("x", null, null, null, 1, 10);
    }

    @Test
    @DisplayName("Qdrant 检索失败 → 降级关键词搜索")
    void search_indexFails_fallback() {
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.1f});
        when(vectorIndex.search(any(), any(), anyInt())).thenThrow(new RuntimeException("qdrant 挂了"));
        Page<ProductListItemResponse> page = new Page<>(1, 10);
        page.setRecords(List.of(ProductListItemResponse.builder().id(8L).name("kw").build()));
        when(productService.searchProducts(anyString(), any(), any(), any(), eq(1), eq(10))).thenReturn(page);

        List<ProductListItemResponse> out = service.search("x", null, null, null, 10);

        assertEquals(8L, out.get(0).getId());
    }

    @Test
    @DisplayName("reindexAll：每个未删商品入队一条 UPSERT 事件")
    void reindexAll_enqueuesUpsertPerProduct() {
        when(productMapper.selectList(null))
                .thenReturn(List.of(product(1L, 100L, "A"), product(2L, 100L, "B")));

        int n = service.reindexAll();

        assertEquals(2, n);
        ArgumentCaptor<IndexOutbox> cap = ArgumentCaptor.forClass(IndexOutbox.class);
        verify(indexOutboxMapper, times(2)).insert(cap.capture());
        assertTrue(cap.getAllValues().stream().allMatch(e -> "UPSERT".equals(e.getOp())));
        assertTrue(cap.getAllValues().stream().allMatch(e -> "PENDING".equals(e.getStatus())));
    }
}
