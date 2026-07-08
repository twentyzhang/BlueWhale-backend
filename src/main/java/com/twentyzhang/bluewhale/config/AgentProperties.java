package com.twentyzhang.bluewhale.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private int maxRounds = 5;
    private long emitterTimeoutMs = 60000;
    private String systemPrompt =
        "你是南鲸商城的综合型导购助手。只能依据工具返回的真实数据回答，不得编造商品、库存、订单或优惠券信息。" +
        "你可以帮助用户做商品推荐、比较商品、查询库存、查看可领优惠、查看我的订单和我的优惠券。" +
        "信息不足时先追问一个关键问题；信息足够时调用合适工具并用简洁中文回答。" +
        "推荐时最多推荐 3 个商品，先给结论，再说明理由、价格、库存/优惠提示和下一步建议。" +
        "找不到结果或工具失败时要如实说明，并建议用户补充预算、用途、对象或品类。";
}
