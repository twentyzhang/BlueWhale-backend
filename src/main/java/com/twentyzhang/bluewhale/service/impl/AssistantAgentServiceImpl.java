package com.twentyzhang.bluewhale.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.config.AgentProperties;
import com.twentyzhang.bluewhale.service.AgentChatClient;
import com.twentyzhang.bluewhale.service.AssistantAgentService;
import com.twentyzhang.bluewhale.service.llm.AgentMessage;
import com.twentyzhang.bluewhale.service.llm.AgentTurn;
import com.twentyzhang.bluewhale.service.llm.ToolCall;
import com.twentyzhang.bluewhale.service.tool.AgentContext;
import com.twentyzhang.bluewhale.service.tool.Tool;
import com.twentyzhang.bluewhale.service.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class AssistantAgentServiceImpl implements AssistantAgentService {

    private final AgentChatClient client;
    private final ToolRegistry registry;
    private final AgentProperties props;
    private final ObjectMapper om;
    private final Executor executor;

    public AssistantAgentServiceImpl(AgentChatClient client, ToolRegistry registry,
                                     AgentProperties props, ObjectMapper om,
                                     @Qualifier("assistantStreamExecutor") Executor executor) {
        this.client = client; this.registry = registry; this.props = props;
        this.om = om; this.executor = executor;
    }

    @Override
    public void chat(String q, AgentContext ctx, SseEmitter emitter) {
        executor.execute(() -> runLoop(q, ctx, emitter));
    }

    private void runLoop(String q, AgentContext ctx, SseEmitter emitter) {
        List<AgentMessage> messages = new ArrayList<>();
        messages.add(AgentMessage.system(props.getSystemPrompt()));
        messages.add(AgentMessage.user(q));
        List<Map<String, Object>> schemas = registry.toolSchemas();

        try {
            for (int round = 0; round < props.getMaxRounds(); round++) {
                AgentTurn turn = client.chat(messages, schemas);
                if (!turn.hasToolCalls()) {
                    // 收敛：流式产出最终回答
                    client.streamFinal(messages, delta -> send(emitter, "answer", delta));
                    send(emitter, "done", "");
                    emitter.complete();
                    return;
                }
                // 记录 assistant 的 tool_calls，再逐个执行并回灌
                messages.add(AgentMessage.assistantToolCalls(turn.toolCalls()));
                for (ToolCall call : turn.toolCalls()) {
                    String resultJson = executeTool(call, ctx, emitter);
                    messages.add(AgentMessage.tool(call.id(), resultJson));
                }
            }
            // 超最大轮数未收敛
            send(emitter, "error", "这个问题有点复杂，换个问法试试？");
            emitter.complete();
        } catch (Exception e) {
            log.warn("Agent 运行失败：{}", e.getMessage());
            send(emitter, "error", "助手繁忙，请稍后再试");
            emitter.complete();
        }
    }

    /** 执行一个工具：发 step → 执行（命中商品推 products）→ 发 tool；返回回灌给 LLM 的 JSON。 */
    private String executeTool(ToolCall call, AgentContext ctx, SseEmitter emitter) {
        Tool tool;
        try {
            tool = registry.get(call.name());
        } catch (IllegalArgumentException unknown) {
            sendJson(emitter, "tool", Map.of("tool", call.name(), "ok", false));
            return "{\"error\":\"未知工具\"}";
        }
        sendJson(emitter, "step", Map.of("tool", tool.name(), "label", stepLabel(tool.name())));
        try {
            JsonNode args = om.readTree(call.argumentsJson() == null ? "{}" : call.argumentsJson());
            Object result = tool.execute(args, ctx);
            if (tool.producesProducts()) {
                send(emitter, "products", om.writeValueAsString(result));
            }
            sendJson(emitter, "tool", Map.of("tool", tool.name(), "ok", true));
            return om.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("工具 {} 执行失败：{}", tool.name(), e.getMessage());
            sendJson(emitter, "tool", Map.of("tool", tool.name(), "ok", false));
            return "{\"error\":\"工具执行失败：" + safe(e.getMessage()) + "\"}";
        }
    }

    private static String stepLabel(String tool) {
        return switch (tool) {
            case "search_products"       -> "正在搜索商品…";
            case "get_product_detail"    -> "正在查询商品详情…";
            case "check_stock"           -> "正在查询库存…";
            case "list_claimable_coupons"-> "正在查询可领优惠券…";
            case "get_my_orders"         -> "正在查询你的订单…";
            case "list_my_coupons"       -> "正在查询你的优惠券…";
            default                      -> "正在处理…";
        };
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            if ("products".equals(event)) {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } else {
                emitter.send(SseEmitter.event().name(event).data(data));
            }
        } catch (Exception ignored) { /* 客户端断开：停止后续推送 */ }
    }

    private void sendJson(SseEmitter emitter, String event, Object payload) {
        try { send(emitter, event, om.writeValueAsString(payload)); } catch (Exception ignored) {}
    }

    private static String safe(String s) { return s == null ? "" : s.replace("\"", "'"); }
}
