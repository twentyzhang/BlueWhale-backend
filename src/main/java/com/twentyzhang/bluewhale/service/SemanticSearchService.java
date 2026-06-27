package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.dto.ProductListItemResponse;

import java.math.BigDecimal;
import java.util.List;

public interface SemanticSearchService {

    /** 语义搜索（失败降级关键词搜索），返回按相关性排序的商品列表。 */
    List<ProductListItemResponse> search(String q, Long categoryId,
                                         BigDecimal minPrice, BigDecimal maxPrice, int topK);

    /** 给所有未删商品入队 UPSERT 事件，触发全量重建索引；返回入队条数。 */
    int reindexAll();
}
