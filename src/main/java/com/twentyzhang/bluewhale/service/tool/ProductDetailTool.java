package com.twentyzhang.bluewhale.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.twentyzhang.bluewhale.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProductDetailTool implements Tool {
    private final ProductService productService;

    public String name() { return "get_product_detail"; }
    public String description() { return "查询单个商品的详情（名称/价格/库存/评分）。需 productId。"; }
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object",
                "properties", Map.of("productId", Map.of("type", "integer", "description", "商品 ID")),
                "required", List.of("productId"));
    }
    public Object execute(JsonNode a, AgentContext ctx) {
        return productService.getProductById(a.path("productId").asLong());
    }
}
