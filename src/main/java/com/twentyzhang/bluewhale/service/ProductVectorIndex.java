package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.service.vector.ScoredId;
import com.twentyzhang.bluewhale.service.vector.VectorPayload;
import com.twentyzhang.bluewhale.service.vector.VectorSearchFilter;

import java.util.List;

/** 商品向量索引抽象（现 Qdrant 实现，可替换）。 */
public interface ProductVectorIndex {

    /** 确保 collection 存在（不存在则按配置维度/距离创建）；幂等。 */
    void ensureCollection();

    /** 以 productId 作 point id 写入/覆盖向量与 payload。 */
    void upsert(long productId, float[] vector, VectorPayload payload);

    /** 删除指定 point。 */
    void delete(long productId);

    /** 向量检索 top-k，带结构过滤下推。 */
    List<ScoredId> search(float[] vector, VectorSearchFilter filter, int topK);
}
