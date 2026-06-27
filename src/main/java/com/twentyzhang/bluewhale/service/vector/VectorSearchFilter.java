package com.twentyzhang.bluewhale.service.vector;

import java.math.BigDecimal;

/** 语义检索的结构过滤条件（下推 Qdrant），各项可为 null 表示不限。 */
public record VectorSearchFilter(Long categoryId, BigDecimal minPrice, BigDecimal maxPrice) {}
