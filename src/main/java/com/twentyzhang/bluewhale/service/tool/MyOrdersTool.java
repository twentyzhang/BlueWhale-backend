package com.twentyzhang.bluewhale.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.twentyzhang.bluewhale.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MyOrdersTool implements Tool {
    private final OrderService orderService;

    public String name() { return "get_my_orders"; }
    public String description() { return "查询当前登录用户自己的订单（状态/物流）。可选 status 过滤。"; }
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object",
                "properties", Map.of("status", Map.of("type", "string",
                        "description", "可选订单状态：PENDING_PAYMENT/PAID/SHIPPED/COMPLETED/CANCELLED")));
    }
    public Object execute(JsonNode a, AgentContext ctx) {
        String status = a.has("status") && !a.get("status").isNull() ? a.get("status").asText() : null;
        // 始终用 ctx.userId()，忽略 args 里的任何用户标识，杜绝越权
        return orderService.getMyOrders(ctx.userId(), status, 1, 10).getRecords();
    }
}
