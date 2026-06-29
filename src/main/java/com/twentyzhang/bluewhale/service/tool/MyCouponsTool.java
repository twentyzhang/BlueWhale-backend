package com.twentyzhang.bluewhale.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.twentyzhang.bluewhale.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MyCouponsTool implements Tool {
    private final CouponService couponService;

    public String name() { return "list_my_coupons"; }
    public String description() { return "查询当前登录用户已领取的优惠券。可选 status（UNUSED/USED/EXPIRED）过滤。"; }
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object",
                "properties", Map.of("status", Map.of("type", "string",
                        "description", "可选：UNUSED/USED/EXPIRED")));
    }
    public Object execute(JsonNode a, AgentContext ctx) {
        String status = a.has("status") && !a.get("status").isNull() ? a.get("status").asText() : null;
        return couponService.getMyCoupons(ctx.userId(), status);
    }
}
