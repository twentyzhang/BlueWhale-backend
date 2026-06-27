package com.twentyzhang.bluewhale.service.vector;

import java.math.BigDecimal;

/** 写入 Qdrant point 的结构化 payload（供过滤下推与排查）。 */
public record VectorPayload(Long storeId, Long categoryId, BigDecimal price, String name) {}
