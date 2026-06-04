package com.twentyzhang.bluewhale.util;

import java.time.LocalDate;

/**
 * 缓存 key 与 TTL 常量集中管理。
 *
 * <p>TTL 单位均为秒；{@code *_JITTER} 为雪崩防护的随机抖动上限（实际 TTL = base + random(jitter)）。
 * key 前缀（{@code *_PREFIX}）供 {@code RedisUtil.deleteByPrefix} 批量失效使用。
 */
public final class CacheKeys {

    private CacheKeys() {}

    // ── 商品分类树 ──（近乎静态，TTL 长）
    public static final String CATEGORY_TREE = "category:tree";
    public static final long   CATEGORY_TREE_TTL    = 3600;
    public static final long   CATEGORY_TREE_JITTER = 300;

    // ── 商店详情 ──
    public static final long STORE_DETAIL_TTL    = 1800;
    public static final long STORE_DETAIL_JITTER = 300;
    public static String storeDetail(Long storeId) { return "store:detail:" + storeId; }

    // ── 商店列表（分页）──
    public static final String STORE_LIST_PREFIX = "store:list:";
    public static final long   STORE_LIST_TTL    = 600;
    public static final long   STORE_LIST_JITTER = 120;
    public static String storeList(int page, int size) { return STORE_LIST_PREFIX + page + ":" + size; }

    // ── 商品详情 ──（含 stock，TTL 短，方案 1）
    public static final long PRODUCT_DETAIL_TTL    = 300;
    public static final long PRODUCT_DETAIL_JITTER = 60;
    public static String productDetail(Long productId) { return "product:detail:" + productId; }

    // ── 商品评论（分页）──
    public static final long PRODUCT_REVIEWS_TTL    = 600;
    public static final long PRODUCT_REVIEWS_JITTER = 120;
    public static String productReviewsPrefix(Long productId) { return "product:reviews:" + productId + ":"; }
    public static String productReviews(Long productId, int page, int size) {
        return productReviewsPrefix(productId) + page + ":" + size;
    }

    // ── 订单报表 ──（聚合昂贵、容忍延迟，纯 TTL 失效）
    public static final long REPORT_TTL    = 300;
    public static final long REPORT_JITTER = 60;
    public static String storeReport(Long storeId, LocalDate start, LocalDate end) {
        return "report:store:" + storeId + ":" + start + ":" + end;
    }
    public static String globalReport(LocalDate start, LocalDate end) {
        return "report:global:" + start + ":" + end;
    }
}
