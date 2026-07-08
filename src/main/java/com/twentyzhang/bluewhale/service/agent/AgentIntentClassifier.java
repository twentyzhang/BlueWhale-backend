package com.twentyzhang.bluewhale.service.agent;

import org.springframework.stereotype.Component;

@Component
public class AgentIntentClassifier {

    public AgentIntent classify(String q) {
        String text = normalize(q);
        if (text.isBlank()) {
            return AgentIntent.GENERAL_GUIDANCE;
        }

        if (containsAny(text, "我的订单", "订单", "物流", "到哪", "买过", "上次买过")) {
            return AgentIntent.PERSONAL_ORDER;
        }
        if (isPersonalCouponQuestion(text)) {
            return AgentIntent.PERSONAL_COUPON;
        }
        if (containsAny(text, "库存", "有货", "还有货", "还有限", "价格", "多少钱", "详情")) {
            return AgentIntent.STOCK_OR_DETAIL;
        }
        if (isBareRecommendation(text)) {
            return AgentIntent.UNCLEAR;
        }
        if (containsAny(text, "推荐", "适合", "预算", "想买", "夏天", "长辈", "礼物", "无糖", "健康", "饮料", "饭")) {
            return AgentIntent.PRODUCT_RECOMMENDATION;
        }
        if (containsAny(text, "优惠券", "券", "优惠", "能用")) {
            return AgentIntent.PERSONAL_COUPON;
        }
        return AgentIntent.GENERAL_GUIDANCE;
    }

    private static String normalize(String q) {
        return q == null ? "" : q.trim().replace(" ", "");
    }

    private static boolean isBareRecommendation(String text) {
        return containsAny(text, "推荐点东西", "买什么好", "有什么推荐", "推荐一下", "随便推荐")
                && !containsAny(text, "预算", "送", "长辈", "健康", "无糖", "礼物", "适合", "夏天");
    }

    private static boolean isPersonalCouponQuestion(String text) {
        return containsAny(text, "我的券", "我的优惠券", "我的优惠", "我有哪些券", "我有券", "我有优惠", "已领取", "领过");
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
