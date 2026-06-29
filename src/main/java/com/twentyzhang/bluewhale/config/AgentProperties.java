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
        "你是南鲸商城的导购助手。只能依据工具返回的真实数据回答，不得编造商品、库存、订单或优惠券信息。" +
        "需要数据时调用合适的工具；信息足够时直接用简洁中文回答并给出购买建议。找不到就如实说明。";
}
