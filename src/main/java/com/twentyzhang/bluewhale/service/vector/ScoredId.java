package com.twentyzhang.bluewhale.service.vector;

/** 向量检索命中：商品 id + 相似度分数。 */
public record ScoredId(long productId, double score) {}
