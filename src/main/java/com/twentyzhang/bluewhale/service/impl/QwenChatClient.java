package com.twentyzhang.bluewhale.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.config.RagProperties;
import com.twentyzhang.bluewhale.service.ChatCompletionClient;
import com.twentyzhang.bluewhale.service.llm.ChatMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class QwenChatClient implements ChatCompletionClient {

    private final RagProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient rest;

    public QwenChatClient(RagProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) props.getTimeoutMs());
        f.setReadTimeout((int) props.getTimeoutMs());
        this.rest = RestClient.builder()
                .baseUrl(props.getQwen().getBaseUrl())
                .requestFactory(f)
                .build();
    }

    @Override
    @CircuitBreaker(name = "aiLlm", fallbackMethod = "streamChatFallback")
    public void streamChat(List<ChatMessage> messages, Consumer<String> onDelta) {
        var q = props.getQwen();
        Map<String, Object> body = new HashMap<>();
        body.put("model", q.getModel());
        body.put("stream", true);
        body.put("temperature", q.getTemperature());
        body.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content())).toList());

        rest.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + q.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("qwen HTTP " + response.getStatusCode());
                    }
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        consumeStream(reader, onDelta);
                    }
                    return null;
                });
    }

    /** 熔断降级：抛 IllegalStateException，由 RagServiceImpl.answer() 的 executor lambda catch 推 error SSE 事件。 */
    public void streamChatFallback(List<ChatMessage> messages, Consumer<String> onDelta, Throwable t) {
        log.warn("LLM streamChat 熔断降级：{}", t.getMessage());
        throw new IllegalStateException("LLM 暂不可用", t);
    }

    /** 逐行消费 OpenAI 风格 SSE：`data: {json}` 取 delta.content，`data: [DONE]` 终止。包级可见供单测。 */
    void consumeStream(BufferedReader reader, Consumer<String> onDelta) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring("data:".length()).trim();
            if (payload.isEmpty()) {
                continue;
            }
            if ("[DONE]".equals(payload)) {
                break;
            }
            String delta = extractDelta(payload);
            if (delta != null && !delta.isEmpty()) {
                onDelta.accept(delta);
            }
        }
    }

    /** 从一段 chunk JSON 取 {@code choices[0].delta.content}；缺失或非法返回 null。包级可见供单测。 */
    String extractDelta(String json) {
        try {
            JsonNode content = objectMapper.readTree(json)
                    .path("choices").path(0).path("delta").path("content");
            return content.isTextual() ? content.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
