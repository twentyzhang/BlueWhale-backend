package com.twentyzhang.bluewhale.service.agent;

import com.twentyzhang.bluewhale.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentPromptComposer {

    private final AgentProperties props;

    public String compose(AgentIntent intent) {
        AgentIntent safeIntent = intent == null ? AgentIntent.GENERAL_GUIDANCE : intent;
        return basePrompt()
                + "\n\n综合型导购规则："
                + "\n- 只能基于工具返回的真实数据回答，不得编造商品、库存、订单或优惠券信息。"
                + "\n- 信息不足时只追问一个关键问题，不要连续追问多个问题。"
                + "\n- 推荐时最多推荐3个商品，结论先行，再说明理由、价格、库存或优惠，最后给下一步建议。"
                + "\n- 工具结果为空或失败时如实说明，并给出用户可以补充的条件。"
                + "\n- 涉及个人订单/优惠券时必须优先围绕当前登录用户，不要替用户猜测或越权查询。"
                + "\n\n当前意图：" + safeIntent.name()
                + "\n" + hint(safeIntent);
    }

    private String basePrompt() {
        String systemPrompt = props.getSystemPrompt();
        return systemPrompt == null ? "" : systemPrompt;
    }

    private static String hint(AgentIntent intent) {
        return switch (intent) {
            case PRODUCT_RECOMMENDATION ->
                    "推荐场景：优先调用 search_products；必要时再调用 get_product_detail / check_stock / list_claimable_coupons。回答要结论先行，解释差异，最后补充建议。";
            case PERSONAL_ORDER ->
                    "订单场景：优先调用 get_my_orders；只总结当前登录用户的订单，不要猜测订单状态，也不要推断不存在的订单。";
            case PERSONAL_COUPON ->
                    "优惠券场景：优先调用 list_my_coupons 查询当前登录用户已领取的券；必要时再调用 list_claimable_coupons 查询可领取券。";
            case STOCK_OR_DETAIL ->
                    "库存/详情场景：如果用户没有给 productId，先用 search_products 定位商品；拿到 productId 后再调用 get_product_detail 或 check_stock。";
            case UNCLEAR ->
                    "模糊场景：如果仍进入模型循环，请先用简短中文追问一个关键条件。";
            case GENERAL_GUIDANCE ->
                    "一般导购场景：可以先调用 search_products 获取真实商品，再给保守建议。";
        };
    }
}
