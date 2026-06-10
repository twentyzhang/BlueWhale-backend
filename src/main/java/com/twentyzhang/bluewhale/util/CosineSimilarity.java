package com.twentyzhang.bluewhale.util;

import java.util.Map;

/**
 * 加权余弦相似度（item-based CF）。
 * 向量以 userId → 兴趣权重 表示；两商品的相似度 = 共同用户上的加权余弦。
 * 分母的模长对「热门商品」起归一化压制作用（见学习笔记 3.1）。
 */
public final class CosineSimilarity {

    private CosineSimilarity() {}

    /**
     * @param a 商品 A 的用户权重向量（userId → weight）
     * @param b 商品 B 的用户权重向量
     * @return 余弦相似度 [0,1]；任一为空或无重叠返回 0
     */
    public static double cosine(Map<Long, Double> a, Map<Long, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        // 遍历较小的 map 求点积，省一半查找
        Map<Long, Double> small = a.size() <= b.size() ? a : b;
        Map<Long, Double> large = small == a ? b : a;

        double dot = 0.0;
        for (Map.Entry<Long, Double> e : small.entrySet()) {
            Double w = large.get(e.getKey());
            if (w != null) {
                dot += e.getValue() * w;
            }
        }
        if (dot == 0.0) {
            return 0.0;
        }
        double normProduct = norm(a) * norm(b);
        return normProduct == 0.0 ? 0.0 : dot / normProduct;
    }

    private static double norm(Map<Long, Double> v) {
        double sum = 0.0;
        for (double w : v.values()) {
            sum += w * w;
        }
        return Math.sqrt(sum);
    }
}
