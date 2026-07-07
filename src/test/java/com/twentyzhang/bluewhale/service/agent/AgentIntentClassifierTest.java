package com.twentyzhang.bluewhale.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentIntentClassifierTest {

    private final AgentIntentClassifier classifier = new AgentIntentClassifier();

    @Test
    void vagueRecommendation_isUnclear() {
        assertThat(classifier.classify("推荐点东西")).isEqualTo(AgentIntent.UNCLEAR);
        assertThat(classifier.classify("买什么好")).isEqualTo(AgentIntent.UNCLEAR);
    }

    @Test
    void concreteRecommendation_isProductRecommendation() {
        assertThat(classifier.classify("送长辈的健康礼物，预算100"))
                .isEqualTo(AgentIntent.PRODUCT_RECOMMENDATION);
        assertThat(classifier.classify("夏天喝点无糖的"))
                .isEqualTo(AgentIntent.PRODUCT_RECOMMENDATION);
    }

    @Test
    void personalQuestions_areClassifiedFirst() {
        assertThat(classifier.classify("我的订单到哪了")).isEqualTo(AgentIntent.PERSONAL_ORDER);
        assertThat(classifier.classify("我上次买过什么")).isEqualTo(AgentIntent.PERSONAL_ORDER);
        assertThat(classifier.classify("我有哪些券能用")).isEqualTo(AgentIntent.PERSONAL_COUPON);
    }

    @Test
    void stockOrDetailQuestions_areClassified() {
        assertThat(classifier.classify("这个商品还有货吗")).isEqualTo(AgentIntent.STOCK_OR_DETAIL);
        assertThat(classifier.classify("这个多少钱")).isEqualTo(AgentIntent.STOCK_OR_DETAIL);
    }

    @Test
    void unknownText_fallsBackToGeneralGuidance() {
        assertThat(classifier.classify("你好")).isEqualTo(AgentIntent.GENERAL_GUIDANCE);
        assertThat(classifier.classify(null)).isEqualTo(AgentIntent.GENERAL_GUIDANCE);
    }
}
