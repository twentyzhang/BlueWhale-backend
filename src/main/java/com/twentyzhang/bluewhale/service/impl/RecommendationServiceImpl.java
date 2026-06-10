package com.twentyzhang.bluewhale.service.impl;

import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.ProductSimilarity;
import com.twentyzhang.bluewhale.mapper.ProductCategoryMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.mapper.RecommendationMapper;
import com.twentyzhang.bluewhale.service.RecommendationService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.CosineSimilarity;
import com.twentyzhang.bluewhale.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final RecommendationMapper recommendationMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final RedisUtil redisUtil;

    /** 每个商品存储的相似商品上限。public 便于测试跨包引用。 */
    public static final int TOP_N = 50;
    /** 相似度缓存键前缀（ZSET：member=similarProductId，score=相似度）。 */
    static final String SIM_KEY_PREFIX = "rec:sim:";
    /** 相似度缓存 TTL（秒），1 天，下次重建会清缓存。 */
    static final long SIM_TTL_SECONDS = 24 * 60 * 60;
    /** 空相似结果哨兵键前缀（冷门商品无相似时占位，避免反复回表）。前缀含 rec:sim: 故重建清缓存时一并清除。 */
    static final String EMPTY_SENTINEL_PREFIX = "rec:sim:empty:";
    /** 空结果哨兵 TTL（秒），5 分钟。 */
    static final long EMPTY_SENTINEL_TTL_SECONDS = 5 * 60;

    /** 订单状态 → 兴趣权重。集中定义，便于调参（spec §2.2）。 */
    static double weightOf(String status) {
        return switch (status) {
            case "PAID", "SHIPPED", "COMPLETED" -> 1.0;
            case "PENDING_PAYMENT" -> 0.5;
            case "CANCELLED" -> 0.3;
            default -> 0.0;
        };
    }

    /**
     * 评分 → 兴趣权重乘子（第二轮 C3）：高分增强、低分削弱该用户对该商品的兴趣信号，无评分按 1.0。
     * public 便于测试跨包引用。
     */
    public static double ratingMultiplier(Integer rating) {
        if (rating == null) {
            return 1.0;
        }
        return switch (rating) {
            case 5 -> 1.5;
            case 4 -> 1.2;
            case 2 -> 0.7;
            case 1 -> 0.5;
            default -> 1.0;   // 3 星及异常值视为中性
        };
    }

    // ============================ 重建（离线 / Admin 手动） ============================

    @Override
    @Transactional
    public int rebuildAll() {
        List<Map<String, Object>> rows = recommendationMapper.selectInteractions(null);
        Map<String, Integer> ratings = loadRatings();
        // productId -> (userId -> 最大权重，再按评分加权)
        Map<Long, Map<Long, Double>> vectors = buildVectors(rows, ratings);

        recommendationMapper.deleteAllSimilarities();

        List<Long> productIds = new ArrayList<>(vectors.keySet());
        int written = 0;
        for (Long pid : productIds) {
            Map<Long, Double> va = vectors.get(pid);
            List<ProductSimilarity> sims = new ArrayList<>();
            for (Long other : productIds) {
                if (other.equals(pid)) {
                    continue;
                }
                double sim = CosineSimilarity.cosine(va, vectors.get(other));
                if (sim <= 0.0) {
                    continue;
                }
                sims.add(ProductSimilarity.builder()
                        .productId(pid)
                        .similarProductId(other)
                        .score(BigDecimal.valueOf(sim).setScale(8, RoundingMode.HALF_UP))
                        .build());
            }
            sims.sort((x, y) -> y.getScore().compareTo(x.getScore()));
            int limit = Math.min(sims.size(), TOP_N);
            for (int i = 0; i < limit; i++) {
                recommendationMapper.insert(sims.get(i));
                written++;
            }
        }
        // 清相似度缓存，read-through 懒重建（避免重建时大量写 Redis）
        redisUtil.deleteByPrefix(SIM_KEY_PREFIX);
        log.info("推荐相似度重建完成：商品 {} 个，相似度 {} 条", productIds.size(), written);
        return written;
    }

    /**
     * 从原始交互行构建 productId -> (userId -> 权重) 向量：先取状态最大权重，再乘以评分乘子（C3）。
     *
     * @param ratingByUserProduct key="userId:productId" → 评分（1-5），无评分则缺省
     */
    private Map<Long, Map<Long, Double>> buildVectors(List<Map<String, Object>> rows,
                                                      Map<String, Integer> ratingByUserProduct) {
        Map<Long, Map<Long, Double>> vectors = new HashMap<>();
        for (Map<String, Object> row : rows) {
            double w = weightOf((String) row.get("status"));
            if (w <= 0.0) {
                continue;
            }
            Long userId = ((Number) row.get("userId")).longValue();
            Long productId = ((Number) row.get("productId")).longValue();
            vectors.computeIfAbsent(productId, k -> new HashMap<>()).merge(userId, w, Math::max);
        }
        // 评分加权：对每个 (用户,商品) 的状态权重乘以评分乘子
        if (!ratingByUserProduct.isEmpty()) {
            for (Map.Entry<Long, Map<Long, Double>> pe : vectors.entrySet()) {
                Long productId = pe.getKey();
                for (Map.Entry<Long, Double> ue : pe.getValue().entrySet()) {
                    Integer rating = ratingByUserProduct.get(ue.getKey() + ":" + productId);
                    if (rating != null) {
                        ue.setValue(ue.getValue() * ratingMultiplier(rating));
                    }
                }
            }
        }
        return vectors;
    }

    /** 加载评分映射 key="userId:productId" → 最高分（同用户同商品多评取最强满意信号）。 */
    private Map<String, Integer> loadRatings() {
        Map<String, Integer> map = new HashMap<>();
        for (Map<String, Object> r : recommendationMapper.selectUserProductRatings()) {
            Long userId = ((Number) r.get("userId")).longValue();
            Long productId = ((Number) r.get("productId")).longValue();
            Integer rating = ((Number) r.get("rating")).intValue();
            map.merge(userId + ":" + productId, rating, Math::max);
        }
        return map;
    }

    // ============================ 接口 A：商品相关推荐 ============================

    @Override
    public List<ProductListItemResponse> getRelated(Long productId, int limit) {
        List<Long> ids = readSimilarIds(productId, limit);
        if (ids.size() < limit) {
            Product p = productMapper.selectById(productId);
            Long categoryId = (p == null) ? null : p.getCategoryId();
            Set<Long> exclude = new HashSet<>(ids);
            exclude.add(productId);                       // 不把自己当推荐
            fillWithHot(ids, categoryId, limit, exclude);
        }
        return toListItems(ids, limit);
    }

    /** 读相似商品 ID（read-through：先 ZSET，未命中回表并回填）。 */
    private List<Long> readSimilarIds(Long productId, int limit) {
        String key = SIM_KEY_PREFIX + productId;
        List<String> cached = redisUtil.zRevRange(key, 0, limit - 1);
        if (!cached.isEmpty()) {
            return cached.stream().map(Long::valueOf).collect(Collectors.toList());
        }
        // 空结果哨兵：已知该商品无相似（冷门/新品）时直接返回，避免反复回表（C1）
        if (Boolean.TRUE.equals(redisUtil.hasKey(EMPTY_SENTINEL_PREFIX + productId))) {
            return new ArrayList<>();
        }
        List<ProductSimilarity> rows = recommendationMapper.selectTopSimilar(productId, TOP_N);
        if (rows.isEmpty()) {
            redisUtil.setWithExpire(EMPTY_SENTINEL_PREFIX + productId, "1",
                    EMPTY_SENTINEL_TTL_SECONDS, TimeUnit.SECONDS);
            return new ArrayList<>();
        }
        for (ProductSimilarity r : rows) {
            redisUtil.zAdd(key, String.valueOf(r.getSimilarProductId()), r.getScore().doubleValue());
        }
        redisUtil.expire(key, SIM_TTL_SECONDS, TimeUnit.SECONDS);
        return rows.stream().limit(limit).map(ProductSimilarity::getSimilarProductId)
                .collect(Collectors.toList());
    }

    /** 用「同类目热销 → 全站热销」补足 ids 至 limit，去重并排除 exclude。 */
    private void fillWithHot(List<Long> ids, Long categoryId, int limit, Set<Long> exclude) {
        if (categoryId != null) {
            appendUnique(ids, recommendationMapper.selectCategoryHotProductIds(categoryId, limit * 2), limit, exclude);
        }
        if (ids.size() < limit) {
            appendUnique(ids, recommendationMapper.selectGlobalHotProductIds(limit * 2), limit, exclude);
        }
    }

    private void appendUnique(List<Long> ids, List<Long> candidates, int limit, Set<Long> exclude) {
        for (Long c : candidates) {
            if (ids.size() >= limit) {
                return;
            }
            if (exclude.contains(c) || ids.contains(c)) {
                continue;
            }
            ids.add(c);
        }
    }

    // ============================ 接口 B：个性化猜你喜欢 ============================

    @Override
    public List<ProductListItemResponse> getPersonalized(int limit) {
        Long userId = AuthUtil.getCurrentUser().userId();
        List<Map<String, Object>> rows = recommendationMapper.selectInteractions(userId);

        // 用户已交互商品 → 最大权重
        Map<Long, Double> mine = new HashMap<>();
        for (Map<String, Object> row : rows) {
            double w = weightOf((String) row.get("status"));
            if (w > 0.0) {
                mine.merge(((Number) row.get("productId")).longValue(), w, Math::max);
            }
        }

        // 新用户 / 无有效历史 → 全站热销
        if (mine.isEmpty()) {
            List<Long> hot = recommendationMapper.selectGlobalHotProductIds(limit);
            return toListItems(hot, limit);
        }

        // 聚合：对每个已购商品取相似集，按 w(u,h)·score 累加，排除已购
        Set<Long> purchased = mine.keySet();
        Map<Long, Double> scores = new HashMap<>();
        for (Map.Entry<Long, Double> h : mine.entrySet()) {
            for (ProductSimilarity sim : recommendationMapper.selectTopSimilar(h.getKey(), TOP_N)) {
                Long cand = sim.getSimilarProductId();
                if (purchased.contains(cand)) {
                    continue;
                }
                scores.merge(cand, h.getValue() * sim.getScore().doubleValue(), Double::sum);
            }
        }

        List<Long> ranked = scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 不足补全站热销（排除已购与已在列表）
        if (ranked.size() < limit) {
            Set<Long> exclude = new HashSet<>(purchased);
            appendUnique(ranked, recommendationMapper.selectGlobalHotProductIds(limit * 2), limit, exclude);
        }
        return toListItems(ranked, limit);
    }

    // ============================ 公共：ID 列表 → 摘要 DTO ============================

    /** 商品 ID 列表 → 摘要 DTO（保序，批量查商品 + 类目名，避免 N+1）。 */
    private List<ProductListItemResponse> toListItems(List<Long> ids, int limit) {
        List<Long> finalIds = ids.stream().distinct().limit(limit).collect(Collectors.toList());
        if (finalIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Product> products = new HashMap<>();
        productMapper.selectBatchIds(finalIds).forEach(p -> products.put(p.getId(), p));

        Set<Long> categoryIds = products.values().stream()
                .map(Product::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> categoryNames = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            productCategoryMapper.selectBatchIds(categoryIds)
                    .forEach(c -> categoryNames.put(c.getId(), c.getName()));
        }

        List<ProductListItemResponse> result = new ArrayList<>();
        for (Long id : finalIds) {
            Product p = products.get(id);
            if (p == null) {
                continue;                                  // 商品已删，跳过
            }
            result.add(ProductListItemResponse.builder()
                    .id(p.getId()).name(p.getName()).price(p.getPrice())
                    .stock(p.getStock()).imageUrl(p.getImageUrl())
                    .categoryName(categoryNames.get(p.getCategoryId()))
                    .build());
        }
        return result;
    }
}
