package com.twentyzhang.bluewhale.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.twentyzhang.bluewhale.config.SearchProperties;
import com.twentyzhang.bluewhale.service.EmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TongyiEmbeddingClient implements EmbeddingClient {

    private final SearchProperties props;
    private final RestClient restClient;

    public TongyiEmbeddingClient(SearchProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.getTongyi().getEmbeddingUrl())
                .build();
    }

    @Override
    public float[] embed(String text) {
        var t = props.getTongyi();
        Map<String, Object> body = Map.of(
                "model", t.getEmbeddingModel(),
                "input", Map.of("texts", List.of(text)),
                "parameters", Map.of("dimension", t.getEmbeddingDimension(), "encoding_format", "float"));

        JsonNode resp = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + t.getApiKey())
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode emb = resp == null ? null
                : resp.path("output").path("embeddings").path(0).path("embedding");
        if (emb == null || !emb.isArray() || emb.isEmpty()) {
            throw new IllegalStateException("通义 embedding 返回为空：" + resp);
        }
        float[] vec = new float[emb.size()];
        for (int i = 0; i < emb.size(); i++) {
            vec[i] = (float) emb.get(i).asDouble();
        }
        if (vec.length != t.getEmbeddingDimension()) {
            throw new IllegalStateException(
                    "通义 embedding 维度不符：期望 " + t.getEmbeddingDimension() + "，实得 " + vec.length);
        }
        return vec;
    }
}
