package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.ProductSimilarity;
import com.twentyzhang.bluewhale.mapper.ProductCategoryMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.mapper.RecommendationMapper;
import com.twentyzhang.bluewhale.service.impl.RecommendationServiceImpl;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RecommendationService")
class RecommendationServiceTest extends BaseServiceTest {

    @Mock private RecommendationMapper recommendationMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ProductCategoryMapper productCategoryMapper;
    @Mock private RedisUtil redisUtil;

    @InjectMocks private RecommendationServiceImpl service;

    private static Map<String, Object> interaction(long userId, long productId, String status) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", userId);
        m.put("productId", productId);
        m.put("status", status);
        return m;
    }

    private static Product product(long id, Long categoryId) {
        return Product.builder()
                .id(id).name("商品" + id).price(new BigDecimal("9.90"))
                .stock(100).imageUrl("http://img/" + id).categoryId(categoryId).build();
    }

    // ── 重建 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rebuildAll：手机与手机壳/数据线相似度高于咖啡，并清缓存")
    void rebuildAll_computesAndPersists() {
        // 手机=10 手机壳=20 数据线=30 咖啡=40；用户 张三=1 李四=2 王五=3 赵六=4，均 COMPLETED
        List<Map<String, Object>> rows = List.of(
                interaction(1, 10, "COMPLETED"), interaction(1, 20, "COMPLETED"), interaction(1, 30, "COMPLETED"),
                interaction(2, 10, "COMPLETED"), interaction(2, 20, "COMPLETED"), interaction(2, 40, "COMPLETED"),
                interaction(3, 10, "COMPLETED"), interaction(3, 30, "COMPLETED"),
                interaction(4, 40, "COMPLETED")
        );
        when(recommendationMapper.selectInteractions(null)).thenReturn(rows);
        when(recommendationMapper.selectUserProductRatings()).thenReturn(List.of());

        int written = service.rebuildAll();

        verify(recommendationMapper).deleteAllSimilarities();
        verify(redisUtil).deleteByPrefix("rec:sim:");
        assertTrue(written > 0);

        ArgumentCaptor<ProductSimilarity> cap = ArgumentCaptor.forClass(ProductSimilarity.class);
        verify(recommendationMapper, atLeastOnce()).insert(cap.capture());

        // 手机(10)→手机壳(20) 的分数 应高于 手机(10)→咖啡(40)
        double phoneCase = scoreOf(cap.getAllValues(), 10L, 20L);
        double phoneCoffee = scoreOf(cap.getAllValues(), 10L, 40L);
        assertTrue(phoneCase > phoneCoffee,
                "手机-手机壳(" + phoneCase + ") 应 > 手机-咖啡(" + phoneCoffee + ")");
    }

    private static double scoreOf(List<ProductSimilarity> all, long pid, long sid) {
        return all.stream()
                .filter(s -> s.getProductId() == pid && s.getSimilarProductId() == sid)
                .findFirst().orElseThrow().getScore().doubleValue();
    }

    // ── 评分加权（C3） ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ratingMultiplier：高分增强、低分削弱、无评分中性")
    void ratingMultiplier_mapsRatingToFactor() {
        assertEquals(1.5, RecommendationServiceImpl.ratingMultiplier(5));
        assertEquals(1.2, RecommendationServiceImpl.ratingMultiplier(4));
        assertEquals(1.0, RecommendationServiceImpl.ratingMultiplier(3));
        assertEquals(0.7, RecommendationServiceImpl.ratingMultiplier(2));
        assertEquals(0.5, RecommendationServiceImpl.ratingMultiplier(1));
        assertEquals(1.0, RecommendationServiceImpl.ratingMultiplier(null));
    }

    @Test
    @DisplayName("rebuildAll：评分加权改变相似度（同被两人购买，一人高分评价使向量偏移）")
    void rebuildAll_appliesRatingWeighting() {
        // 商品10、20 同被用户1、2 购买(COMPLETED) → 无评分时相似度=1.0
        List<Map<String, Object>> rows = List.of(
                interaction(1, 10, "COMPLETED"), interaction(2, 10, "COMPLETED"),
                interaction(1, 20, "COMPLETED"), interaction(2, 20, "COMPLETED"));
        when(recommendationMapper.selectInteractions(null)).thenReturn(rows);
        // 用户1 给商品10 打 5 星（×1.5）→ 商品10 向量被拉偏 → 10↔20 相似度 < 1.0
        Map<String, Object> rating = new HashMap<>();
        rating.put("userId", 1L);
        rating.put("productId", 10L);
        rating.put("rating", 5);
        when(recommendationMapper.selectUserProductRatings()).thenReturn(List.of(rating));

        service.rebuildAll();

        ArgumentCaptor<ProductSimilarity> cap = ArgumentCaptor.forClass(ProductSimilarity.class);
        verify(recommendationMapper, atLeastOnce()).insert(cap.capture());
        double sim10to20 = scoreOf(cap.getAllValues(), 10L, 20L);
        assertTrue(sim10to20 > 0.0 && sim10to20 < 1.0,
                "评分加权后 10↔20 相似度应落在 (0,1)，实际 " + sim10to20);
    }

    // ── 接口 A ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getRelated 缓存命中：直接用 ZSET，不回表")
    void getRelated_cacheHit() {
        when(redisUtil.zRevRange("rec:sim:10", 0, 1)).thenReturn(List.of("20", "30"));
        when(productMapper.selectBatchIds(anyList()))
                .thenReturn(List.of(product(20, 1L), product(30, 1L)));
        when(productCategoryMapper.selectBatchIds(anySet())).thenReturn(List.of());

        var out = service.getRelated(10L, 2);

        assertEquals(2, out.size());
        verify(recommendationMapper, never()).selectTopSimilar(anyLong(), anyInt());
    }

    @Test
    @DisplayName("getRelated 缓存未命中：回表并回填 ZSET")
    void getRelated_cacheMiss_readThrough() {
        when(redisUtil.zRevRange("rec:sim:10", 0, 1)).thenReturn(List.of());
        when(recommendationMapper.selectTopSimilar(10L, RecommendationServiceImpl.TOP_N))
                .thenReturn(List.of(
                        ProductSimilarity.builder().productId(10L).similarProductId(20L)
                                .score(new BigDecimal("0.80")).build(),
                        ProductSimilarity.builder().productId(10L).similarProductId(30L)
                                .score(new BigDecimal("0.70")).build()));
        when(productMapper.selectBatchIds(anyList()))
                .thenReturn(List.of(product(20, 1L), product(30, 1L)));
        when(productCategoryMapper.selectBatchIds(anySet())).thenReturn(List.of());

        var out = service.getRelated(10L, 2);

        assertEquals(2, out.size());
        verify(redisUtil).zAdd("rec:sim:10", "20", 0.80);
        verify(redisUtil).zAdd("rec:sim:10", "30", 0.70);
        verify(redisUtil).expire(eq("rec:sim:10"), anyLong(), any());
    }

    @Test
    @DisplayName("getRelated 无相似数据：同类目热销兜底")
    void getRelated_fallbackCategoryHot() {
        when(redisUtil.zRevRange("rec:sim:10", 0, 1)).thenReturn(List.of());
        when(recommendationMapper.selectTopSimilar(10L, RecommendationServiceImpl.TOP_N))
                .thenReturn(List.of());
        when(productMapper.selectById(10L)).thenReturn(product(10, 5L));   // 类目 5
        when(recommendationMapper.selectCategoryHotProductIds(5L, 4)).thenReturn(List.of(20L, 30L));
        when(productMapper.selectBatchIds(anyList()))
                .thenReturn(List.of(product(20, 5L), product(30, 5L)));
        when(productCategoryMapper.selectBatchIds(anySet())).thenReturn(List.of());

        var out = service.getRelated(10L, 2);

        assertEquals(2, out.size());
        assertTrue(out.stream().noneMatch(r -> r.getId() == 10L), "兜底不含商品自身");
    }

    @Test
    @DisplayName("getRelated 空相似：写哨兵，第二次请求命中哨兵不再回表（C1）")
    void getRelated_emptySimilar_cachesSentinel() {
        when(redisUtil.zRevRange("rec:sim:10", 0, 4)).thenReturn(List.of());
        when(redisUtil.hasKey("rec:sim:empty:10")).thenReturn(false, true);   // 1st 缺失，2nd 命中
        when(recommendationMapper.selectTopSimilar(10L, RecommendationServiceImpl.TOP_N))
                .thenReturn(List.of());
        when(productMapper.selectById(10L)).thenReturn(product(10, 5L));
        when(recommendationMapper.selectCategoryHotProductIds(5L, 10)).thenReturn(List.of());
        when(recommendationMapper.selectGlobalHotProductIds(10)).thenReturn(List.of());

        service.getRelated(10L, 5);   // 第一次：回表空 → 写哨兵
        service.getRelated(10L, 5);   // 第二次：命中哨兵 → 跳过回表

        verify(redisUtil).setWithExpire(eq("rec:sim:empty:10"), anyString(), anyLong(), any());
        verify(recommendationMapper, times(1)).selectTopSimilar(10L, RecommendationServiceImpl.TOP_N);
    }

    // ── 接口 B ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPersonalized 新用户：无历史 → 全站热销")
    void getPersonalized_newUser_globalHot() {
        mockAuthUser(99L, AuthUtil.ROLE_CUSTOMER, null);
        when(recommendationMapper.selectInteractions(99L)).thenReturn(List.of());
        when(recommendationMapper.selectGlobalHotProductIds(2)).thenReturn(List.of(20L, 30L));
        when(productMapper.selectBatchIds(anyList()))
                .thenReturn(List.of(product(20, 1L), product(30, 1L)));
        when(productCategoryMapper.selectBatchIds(anySet())).thenReturn(List.of());

        var out = service.getPersonalized(2);

        assertEquals(2, out.size());
    }

    @Test
    @DisplayName("getPersonalized 老用户：聚合相似集并排除已购")
    void getPersonalized_aggregatesExcludingPurchased() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        // 用户买过 手机(10)
        when(recommendationMapper.selectInteractions(1L))
                .thenReturn(List.of(interaction(1, 10, "COMPLETED")));
        // 手机的相似：手机壳(20,0.8) 数据线(30,0.8)
        when(recommendationMapper.selectTopSimilar(10L, RecommendationServiceImpl.TOP_N))
                .thenReturn(List.of(
                        ProductSimilarity.builder().productId(10L).similarProductId(20L)
                                .score(new BigDecimal("0.80")).build(),
                        ProductSimilarity.builder().productId(10L).similarProductId(30L)
                                .score(new BigDecimal("0.80")).build()));
        when(productMapper.selectBatchIds(anyList()))
                .thenReturn(List.of(product(20, 1L), product(30, 1L)));
        when(productCategoryMapper.selectBatchIds(anySet())).thenReturn(List.of());
        // ranked(2) < limit(10) 会触发全站热销补位（limit*2=20）；返回空表示无补位
        when(recommendationMapper.selectGlobalHotProductIds(20)).thenReturn(List.of());

        var out = service.getPersonalized(10);

        assertEquals(2, out.size());
        assertTrue(out.stream().noneMatch(r -> r.getId() == 10L), "不推荐已购的手机");
    }
}
