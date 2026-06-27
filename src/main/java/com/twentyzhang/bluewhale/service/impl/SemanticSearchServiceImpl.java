package com.twentyzhang.bluewhale.service.impl;

import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.entity.IndexOutbox;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.ProductCategory;
import com.twentyzhang.bluewhale.mapper.IndexOutboxMapper;
import com.twentyzhang.bluewhale.mapper.ProductCategoryMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.service.EmbeddingClient;
import com.twentyzhang.bluewhale.service.ProductService;
import com.twentyzhang.bluewhale.service.ProductVectorIndex;
import com.twentyzhang.bluewhale.service.SemanticSearchService;
import com.twentyzhang.bluewhale.service.vector.ScoredId;
import com.twentyzhang.bluewhale.service.vector.VectorSearchFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchServiceImpl implements SemanticSearchService {

    private final EmbeddingClient embeddingClient;
    private final ProductVectorIndex vectorIndex;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final ProductService productService;
    private final IndexOutboxMapper indexOutboxMapper;

    @Override
    public List<ProductListItemResponse> search(String q, Long categoryId,
                                                BigDecimal minPrice, BigDecimal maxPrice, int topK) {
        try {
            float[] vec = embeddingClient.embed(q);
            List<ScoredId> hits = vectorIndex.search(
                    vec, new VectorSearchFilter(categoryId, minPrice, maxPrice), topK);
            if (hits.isEmpty()) {
                return List.of();
            }
            List<Long> ids = hits.stream().map(ScoredId::productId).toList();
            Map<Long, Product> byId = new HashMap<>();
            for (Product p : productMapper.selectBatchIds(ids)) {
                byId.put(p.getId(), p);
            }
            // 按命中相关性顺序重排，过滤掉已删/缺失的商品
            List<Product> ordered = new ArrayList<>();
            for (Long id : ids) {
                Product p = byId.get(id);
                if (p != null) ordered.add(p);
            }
            return toListResponse(ordered);
        } catch (Exception e) {
            log.warn("语义搜索失败，降级关键词搜索：{}", e.getMessage());
            return productService.searchProducts(q, categoryId, minPrice, maxPrice, 1, topK).getRecords();
        }
    }

    @Override
    public int reindexAll() {
        // 仅入队 UPSERT，真正同步交给 OutboxRelayJob；不读 SecurityContext（Admin 鉴权在 Controller）
        List<Product> all = productMapper.selectList(null); // 逻辑删除自动过滤
        for (Product p : all) {
            indexOutboxMapper.insert(IndexOutbox.builder()
                    .productId(p.getId()).op("UPSERT").status("PENDING").retryCount(0).build());
        }
        return all.size();
    }

    private List<ProductListItemResponse> toListResponse(List<Product> products) {
        Map<Long, String> categoryNames = buildCategoryNameMap(products);
        return products.stream().map(p -> ProductListItemResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .price(p.getPrice())
                .stock(p.getStock())
                .imageUrl(p.getImageUrl())
                .categoryName(categoryNames.get(p.getCategoryId()))
                .build()).toList();
    }

    private Map<Long, String> buildCategoryNameMap(List<Product> products) {
        List<Long> categoryIds = products.stream()
                .map(Product::getCategoryId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> map = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            for (ProductCategory c : productCategoryMapper.selectBatchIds(categoryIds)) {
                map.put(c.getId(), c.getName());
            }
        }
        return map;
    }
}
