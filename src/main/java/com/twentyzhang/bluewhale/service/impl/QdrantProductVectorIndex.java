package com.twentyzhang.bluewhale.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.twentyzhang.bluewhale.config.SearchProperties;
import com.twentyzhang.bluewhale.service.ProductVectorIndex;
import com.twentyzhang.bluewhale.service.vector.ScoredId;
import com.twentyzhang.bluewhale.service.vector.VectorPayload;
import com.twentyzhang.bluewhale.service.vector.VectorSearchFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class QdrantProductVectorIndex implements ProductVectorIndex {

    private final SearchProperties props;
    private final RestClient rest;

    public QdrantProductVectorIndex(SearchProperties props) {
        this.props = props;
        RestClient.Builder b = RestClient.builder().baseUrl(props.getQdrant().getUrl());
        String key = props.getQdrant().getApiKey();
        if (key != null && !key.isBlank()) {
            b.defaultHeader("api-key", key);
        }
        this.rest = b.build();
    }

    private String coll() { return props.getQdrant().getCollection(); }

    @Override
    public void ensureCollection() {
        // 存在则跳过；不存在（GET 抛 4xx）则创建
        try {
            rest.get().uri("/collections/{c}", coll()).retrieve().body(JsonNode.class);
            return;
        } catch (Exception notFound) {
            // 继续创建
        }
        Map<String, Object> body = Map.of("vectors", Map.of(
                "size", props.getQdrant().getVectorSize(),
                "distance", props.getQdrant().getDistance()));
        rest.put().uri("/collections/{c}", coll())
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class);
        log.info("Qdrant collection {} 已创建", coll());
    }

    @Override
    public void upsert(long productId, float[] vector, VectorPayload payload) {
        Map<String, Object> pl = new HashMap<>();
        pl.put("storeId", payload.storeId());
        pl.put("categoryId", payload.categoryId());
        pl.put("price", payload.price());
        pl.put("name", payload.name());

        Map<String, Object> point = Map.of(
                "id", productId,
                "vector", toList(vector),
                "payload", pl);
        Map<String, Object> body = Map.of("points", List.of(point));

        rest.put().uri("/collections/{c}/points?wait=true", coll())
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class);
    }

    @Override
    public void delete(long productId) {
        Map<String, Object> body = Map.of("points", List.of(productId));
        rest.post().uri("/collections/{c}/points/delete?wait=true", coll())
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class);
    }

    @Override
    public List<ScoredId> search(float[] vector, VectorSearchFilter filter, int topK) {
        Map<String, Object> body = new HashMap<>();
        body.put("vector", toList(vector));
        body.put("limit", topK);
        body.put("with_payload", false);

        List<Map<String, Object>> must = new ArrayList<>();
        if (filter != null && filter.categoryId() != null) {
            must.add(Map.of("key", "categoryId", "match", Map.of("value", filter.categoryId())));
        }
        if (filter != null && (filter.minPrice() != null || filter.maxPrice() != null)) {
            Map<String, Object> range = new HashMap<>();
            if (filter.minPrice() != null) range.put("gte", filter.minPrice());
            if (filter.maxPrice() != null) range.put("lte", filter.maxPrice());
            must.add(Map.of("key", "price", "range", range));
        }
        if (!must.isEmpty()) {
            body.put("filter", Map.of("must", must));
        }

        JsonNode resp = rest.post().uri("/collections/{c}/points/search", coll())
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class);

        List<ScoredId> out = new ArrayList<>();
        JsonNode result = resp == null ? null : resp.path("result");
        if (result != null && result.isArray()) {
            for (JsonNode hit : result) {
                out.add(new ScoredId(hit.path("id").asLong(), hit.path("score").asDouble()));
            }
        }
        return out;
    }

    private static List<Float> toList(float[] v) {
        List<Float> l = new ArrayList<>(v.length);
        for (float f : v) l.add(f);
        return l;
    }
}
