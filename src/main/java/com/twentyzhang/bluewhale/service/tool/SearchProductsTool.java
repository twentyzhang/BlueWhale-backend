package com.twentyzhang.bluewhale.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.service.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SearchProductsTool implements Tool {
    private final SemanticSearchService semanticSearchService;

    public String name() { return "search_products"; }
    public String description() {
        return "按自然语言语义搜索商品。用于商品推荐、场景导购、预算/用途/对象类问题，例如送礼、夏天饮料、做饭调味、无糖食品。返回商品列表。";
    }
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "q", Map.of("type", "string", "description", "查询关键词或自然语言描述"),
                        "categoryId", Map.of("type", "integer", "description", "可选分类 ID"),
                        "minPrice", Map.of("type", "number", "description", "可选最低价"),
                        "maxPrice", Map.of("type", "number", "description", "可选最高价")),
                "required", List.of("q"));
    }
    public boolean producesProducts() { return true; }

    public Object execute(JsonNode a, AgentContext ctx) {
        String q = a.path("q").asText("");
        Long catId = a.has("categoryId") && !a.get("categoryId").isNull() ? a.get("categoryId").asLong() : null;
        BigDecimal min = a.has("minPrice") && !a.get("minPrice").isNull() ? new BigDecimal(a.get("minPrice").asText()) : null;
        BigDecimal max = a.has("maxPrice") && !a.get("maxPrice").isNull() ? new BigDecimal(a.get("maxPrice").asText()) : null;
        List<ProductListItemResponse> r = semanticSearchService.search(q, catId, min, max, 8);
        return r.size() > 8 ? r.subList(0, 8) : r;
    }
}
