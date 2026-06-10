package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.dto.ProductListItemResponse;

import java.util.List;

public interface RecommendationService {

    /** 商品相关推荐（接口 A）：相似商品，不足用同类目热销→全站热销兜底。 */
    List<ProductListItemResponse> getRelated(Long productId, int limit);

    /** 个性化猜你喜欢（接口 B）：聚合当前登录用户已购商品的相似集，排除已购，新用户兜底热销。 */
    List<ProductListItemResponse> getPersonalized(int limit);

    /** 全量重建相似度（离线任务 / Admin 手动触发）。返回写入的相似度行数。不读 SecurityContext。 */
    int rebuildAll();
}
