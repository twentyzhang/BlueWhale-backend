package com.twentyzhang.bluewhale.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.config.RagProperties;
import com.twentyzhang.bluewhale.service.AgentChatClient;
import com.twentyzhang.bluewhale.service.llm.AgentMessage;
import com.twentyzhang.bluewhale.service.llm.AgentTurn;
import com.twentyzhang.bluewhale.service.llm.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class QwenAgentChatClient implements AgentChatClient {

    private final RagProperties props;
    private final ObjectMapper om;
    private final RestClient rest;

    public QwenAgentChatClient(RagProperties props, ObjectMapper om) {
        this.props = props;
        this.om = om;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) props.getTimeoutMs());
        f.setReadTimeout((int) props.getTimeoutMs());
        this.rest = RestClient.builder().baseUrl(props.getQwen().getBaseUrl()).requestFactory(f).build();
    }

    @Override
    public AgentTurn chat(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas) {
        var q = props.getQwen();
        Map<String, Object> body = new HashMap<>();
        body.put("model", q.getModel());
        body.put("temperature", q.getTemperature());
        body.put("messages", toWireMessages(messages));
        if (toolSchemas != null && !toolSchemas.isEmpty()) {
            body.put("tools", toolSchemas);
            body.put("tool_choice", "auto");
        }
        String resp = rest.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + q.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(String.class);
        return parseTurn(resp);
    }

    @Override
    public void streamFinal(List<AgentMessage> messages, Consumer<String> onDelta) {
        var q = props.getQwen();
        Map<String, Object> body = new HashMap<>();
        body.put("model", q.getModel());
        body.put("temperature", q.getTemperature());
        body.put("stream", true);
        body.put("tool_choice", "none");   // 强制只产出文本，不再调工具
        body.put("messages", toWireMessages(messages));
        rest.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + q.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful())
                        throw new IllegalStateException("qwen HTTP " + response.getStatusCode());
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            if (!line.startsWith("data:")) continue;
                            String payload = line.substring(5).trim();
                            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                                if ("[DONE]".equals(payload)) break; else continue;
                            }
                            String delta = extractDelta(payload);
                            if (delta != null && !delta.isEmpty()) onDelta.accept(delta);
                        }
                    }
                    return null;
                });
    }

    /** 解析非流式响应：有 tool_calls 取之，否则取 content 作最终文本。 */
    AgentTurn parseTurn(String responseJson) {
        try {
            JsonNode msg = om.readTree(responseJson).path("choices").path(0).path("message");
            JsonNode calls = msg.path("tool_calls");
            if (calls.isArray() && calls.size() > 0) {
                List<ToolCall> list = new ArrayList<>();
                for (JsonNode c : calls) {
                    list.add(new ToolCall(
                            c.path("id").asText(),
                            c.path("function").path("name").asText(),
                            c.path("function").path("arguments").asText("{}")));
                }
                return new AgentTurn(null, list);
            }
            return new AgentTurn(msg.path("content").asText(""), List.of());
        } catch (Exception e) {
            throw new IllegalStateException("解析 qwen 响应失败", e);
        }
    }

    /** AgentMessage → OpenAI wire 格式。 */
    List<Map<String, Object>> toWireMessages(List<AgentMessage> messages) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (AgentMessage m : messages) {
            Map<String, Object> w = new HashMap<>();
            w.put("role", m.role());
            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (ToolCall tc : m.toolCalls()) {
                    tcs.add(Map.of("id", tc.id(), "type", "function",
                            "function", Map.of("name", tc.name(), "arguments", tc.argumentsJson())));
                }
                w.put("tool_calls", tcs);
                w.put("content", "");
            } else {
                w.put("content", m.content() == null ? "" : m.content());
            }
            if (m.toolCallId() != null) w.put("tool_call_id", m.toolCallId());
            wire.add(w);
        }
        return wire;
    }

    String extractDelta(String json) {
        try {
            JsonNode content = om.readTree(json).path("choices").path(0).path("delta").path("content");
            return content.isTextual() ? content.asText() : null;
        } catch (Exception e) { return null; }
    }
}
