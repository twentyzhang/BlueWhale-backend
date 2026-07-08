package com.twentyzhang.bluewhale.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.twentyzhang.bluewhale.service.CouponGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ListClaimableCouponsTool implements Tool {
    private final CouponGroupService couponGroupService;

    public String name() { return "list_claimable_coupons"; }
    public String description() {
        return "查询当前可领取的优惠券列表。用户问平台或店铺有什么券、有什么优惠可以领时使用。";
    }
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }
    public Object execute(JsonNode a, AgentContext ctx) {
        return couponGroupService.getAvailableCouponGroups(1, 20).getRecords();
    }
}
