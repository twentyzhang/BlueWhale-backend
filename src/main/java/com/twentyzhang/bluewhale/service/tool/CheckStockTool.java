package com.twentyzhang.bluewhale.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.twentyzhang.bluewhale.dto.ProductDetailResponse;
import com.twentyzhang.bluewhale.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CheckStockTool implements Tool {
    private final ProductService productService;

    public String name() { return "check_stock"; }
    public String description() { return "查询某商品当前库存数量（用户问'还有货吗/剩多少'）。需 productId。"; }
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object",
                "properties", Map.of("productId", Map.of("type", "integer", "description", "商品 ID")),
                "required", List.of("productId"));
    }
    public Object execute(JsonNode a, AgentContext ctx) {
        ProductDetailResponse d = productService.getProductById(a.path("productId").asLong());
        return Map.of("productId", d.getId(), "name", d.getName(), "stock", d.getStock());
    }
}
