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
        assertThat(prompt).contains("不得编造商品");
        assertThat(prompt).contains("工具结果为空");
    }

    @Test
    void defaultSystemPrompt_containsGroundingAndEmptyResultGuidance() {
        AgentProperties props = new AgentProperties();

        assertThat(props.getSystemPrompt()).contains("不得编造商品");
        assertThat(props.getSystemPrompt()).contains("找不到结果或工具失败");
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

    @Test
    void stockOrDetailPrompt_searchesBeforeDetailWhenProductIdMissing() {
        AgentProperties props = new AgentProperties();
        props.setSystemPrompt("基础提示");
        AgentPromptComposer composer = new AgentPromptComposer(props);

        String prompt = composer.compose(AgentIntent.STOCK_OR_DETAIL);

        assertThat(prompt).contains("如果用户没有给 productId，先用 search_products 定位商品");
        assertThat(prompt).contains("拿到 productId 后再调用 get_product_detail 或 check_stock");
    }

    @Test
    void nullIntentFallsBackToGeneralGuidance() {
        AgentProperties props = new AgentProperties();
        props.setSystemPrompt("基础提示");
        AgentPromptComposer composer = new AgentPromptComposer(props);

        String prompt = composer.compose(null);

        assertThat(prompt).contains("当前意图：GENERAL_GUIDANCE");
        assertThat(prompt).contains("一般导购场景");
    }
}
