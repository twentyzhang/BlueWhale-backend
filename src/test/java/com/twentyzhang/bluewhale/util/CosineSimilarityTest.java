package com.twentyzhang.bluewhale.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CosineSimilarity")
class CosineSimilarityTest {

    private static final double EPS = 1e-4;

    // 手算例子：手机={张三,李四,王五} 手机壳={张三,李四} 数据线={张三,王五} 咖啡={李四,赵六}
    // 用 userId: 张三=1 李四=2 王五=3 赵六=4，二元权重=1.0
    private static final Map<Long, Double> PHONE = Map.of(1L, 1.0, 2L, 1.0, 3L, 1.0);
    private static final Map<Long, Double> CASE_ = Map.of(1L, 1.0, 2L, 1.0);
    private static final Map<Long, Double> CABLE = Map.of(1L, 1.0, 3L, 1.0);
    private static final Map<Long, Double> COFFEE = Map.of(2L, 1.0, 4L, 1.0);

    @Test
    @DisplayName("手机 vs 手机壳 = 2/√6 ≈ 0.8165")
    void phoneVsCase() {
        assertEquals(2.0 / Math.sqrt(6), CosineSimilarity.cosine(PHONE, CASE_), EPS);
    }

    @Test
    @DisplayName("手机 vs 数据线 = 2/√6 ≈ 0.8165")
    void phoneVsCable() {
        assertEquals(2.0 / Math.sqrt(6), CosineSimilarity.cosine(PHONE, CABLE), EPS);
    }

    @Test
    @DisplayName("手机 vs 咖啡 = 1/√6 ≈ 0.4082（热门被归一化压制，低于手机壳/数据线）")
    void phoneVsCoffee() {
        double sim = CosineSimilarity.cosine(PHONE, COFFEE);
        assertEquals(1.0 / Math.sqrt(6), sim, EPS);
        assertTrue(sim < CosineSimilarity.cosine(PHONE, CASE_));
    }

    @Test
    @DisplayName("无重叠用户 → 相似度 0")
    void noOverlap() {
        assertEquals(0.0, CosineSimilarity.cosine(Map.of(1L, 1.0), Map.of(2L, 1.0)), EPS);
    }

    @Test
    @DisplayName("空向量 → 相似度 0")
    void emptyVector() {
        assertEquals(0.0, CosineSimilarity.cosine(Map.of(), Map.of(1L, 1.0)), EPS);
    }

    @Test
    @DisplayName("加权：方向相同（权重成比例）→ 余弦 1.0")
    void weightedSameDirection() {
        assertEquals(1.0, CosineSimilarity.cosine(Map.of(1L, 1.0), Map.of(1L, 0.5)), EPS);
    }
}
