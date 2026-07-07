package com.twentyzhang.bluewhale.service.agent;

import com.twentyzhang.bluewhale.config.AgentProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPromptComposerTest {

    @Test
    void recommendationPrompt_containsToolGuidanceAndLimits() {
        AgentProperties props = new AgentProperties();
        props.setSystemPrompt("基础提示");
        AgentPromptComposer composer = new AgentPromptComposer(props);

        String prompt = composer.compose(AgentIntent.PRODUCT_RECOMMENDATION);

        assertThat(prompt).contains("基础提示");
        assertThat(prompt).contains("优先调用 search_products");
        assertThat(prompt).contains("最多推荐3个商品");
        assertThat(prompt).contains("结论先行");
    }

    @Test
    void personalCouponPrompt_prioritizesMyCouponsTool() {
        AgentProperties props = new AgentProperties();
        props.setSystemPrompt("基础提示");
        AgentPromptComposer composer = new AgentPromptComposer(props);

        String prompt = composer.compose(AgentIntent.PERSONAL_COUPON);

        assertThat(prompt).contains("优先调用 list_my_coupons");
        assertThat(prompt).contains("当前登录用户");
    }

    @Test
    void personalOrderPrompt_prioritizesMyOrdersTool() {
        AgentProperties props = new AgentProperties();
        props.setSystemPrompt("基础提示");
        AgentPromptComposer composer = new AgentPromptComposer(props);

        String prompt = composer.compose(AgentIntent.PERSONAL_ORDER);

        assertThat(prompt).contains("优先调用 get_my_orders");
        assertThat(prompt).contains("不要猜测订单状态");
    }
}
