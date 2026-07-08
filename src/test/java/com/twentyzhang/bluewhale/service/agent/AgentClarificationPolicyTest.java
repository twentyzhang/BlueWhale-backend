package com.twentyzhang.bluewhale.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentClarificationPolicyTest {

    private final AgentClarificationPolicy policy = new AgentClarificationPolicy();

    @Test
    void unclearIntent_returnsOneQuestion() {
        assertThat(policy.maybeAsk("推荐点东西", AgentIntent.UNCLEAR))
                .hasValue("你是想自己吃/用，还是送人？如果送人，大概预算是多少？");
    }

    @Test
    void concreteRecommendation_doesNotAsk() {
        assertThat(policy.maybeAsk("送长辈的健康礼物，预算100", AgentIntent.PRODUCT_RECOMMENDATION))
                .isEmpty();
        assertThat(policy.maybeAsk("夏天喝点无糖的", AgentIntent.PRODUCT_RECOMMENDATION))
                .isEmpty();
    }

    @Test
    void personalIntent_doesNotAsk() {
        assertThat(policy.maybeAsk("我的订单到哪了", AgentIntent.PERSONAL_ORDER)).isEmpty();
        assertThat(policy.maybeAsk("我有哪些券能用", AgentIntent.PERSONAL_COUPON)).isEmpty();
    }
}
