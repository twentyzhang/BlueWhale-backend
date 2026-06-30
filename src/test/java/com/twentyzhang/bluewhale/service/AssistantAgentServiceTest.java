package com.twentyzhang.bluewhale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.config.AgentProperties;
import com.twentyzhang.bluewhale.config.AiMetrics;
import com.twentyzhang.bluewhale.service.impl.AssistantAgentServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.twentyzhang.bluewhale.service.llm.AgentMessage;
import com.twentyzhang.bluewhale.service.llm.AgentTurn;
import com.twentyzhang.bluewhale.service.llm.ToolCall;
import com.twentyzhang.bluewhale.service.tool.AgentContext;
import com.twentyzhang.bluewhale.service.tool.Tool;
import com.twentyzhang.bluewhale.service.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantAgentServiceTest {

    @Mock AgentChatClient client;
    @Mock SseEmitter emitter;

    private AssistantAgentServiceImpl service(ToolRegistry reg) {
        return new AssistantAgentServiceImpl(client, reg, new AgentProperties(), new ObjectMapper(), Runnable::run,
                new AiMetrics(new SimpleMeterRegistry()));
    }

    static Tool fakeSearch() {
        return new Tool() {
            public String name() { return "search_products"; }
            public String description() { return "搜"; }
            public java.util.Map<String,Object> parametersSchema() { return java.util.Map.of("type","object","properties",java.util.Map.of()); }
            public Object execute(com.fasterxml.jackson.databind.JsonNode a, AgentContext c) { return List.of(); }
            public boolean producesProducts() { return true; }
        };
    }

    @Test
    void normalFlow_toolCallThenFinal_emitsStepToolProductsAnswerDone() throws Exception {
        ToolRegistry reg = new ToolRegistry(List.of(fakeSearch()));
        // 第 1 轮返回 tool_call；第 2 轮返回无工具调用（触发 streamFinal）
        // FIX: brief's mock only stubbed one return, so ALL calls returned tool_calls and streamFinal
        // was never reachable. Chain a second thenReturn for the convergence round.
        when(client.chat(anyList(), anyList()))
                .thenReturn(new AgentTurn(null, List.of(new ToolCall("c1","search_products","{}"))))
                .thenReturn(new AgentTurn("推荐耳机", List.of()));
        doAnswer(inv -> { Consumer<String> cb = inv.getArgument(1); cb.accept("推荐"); cb.accept("耳机"); return null; })
                .when(client).streamFinal(anyList(), any());

        service(reg).chat("耳机", new AgentContext(1L,"CUSTOMER"), emitter);

        // step(1) + products(1) + tool(1) + answer*2(2) + done(1) = exactly 6 sends
        verify(emitter, times(6)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).complete();
        verify(client, times(1)).streamFinal(anyList(), any());
        // chat() called exactly twice: round-0 (tool call) + round-1 (convergence)
        verify(client, times(2)).chat(anyList(), anyList());
    }

    @Test
    void maxRounds_exceeded_emitsError() throws Exception {
        ToolRegistry reg = new ToolRegistry(List.of(fakeSearch()));
        AgentProperties props = new AgentProperties();
        props.setMaxRounds(2);
        // 永远返回 tool_call，制造不收敛
        when(client.chat(anyList(), anyList()))
                .thenReturn(new AgentTurn(null, List.of(new ToolCall("c","search_products","{}"))));
        AssistantAgentServiceImpl svc =
                new AssistantAgentServiceImpl(client, reg, props, new ObjectMapper(), Runnable::run,
                        new AiMetrics(new SimpleMeterRegistry()));

        svc.chat("耳机", new AgentContext(1L,"CUSTOMER"), emitter);

        // streamFinal must NEVER be called — proves the loop didn't converge
        verify(client, never()).streamFinal(anyList(), any());
        // emitter must complete — proves the loop terminated (not hung)
        verify(emitter).complete();
        // chat() called exactly maxRounds times — proves the loop ran to the limit, not fewer
        verify(client, times(2)).chat(anyList(), anyList());
    }

    @Test
    void disconnect_stopsRemainingRounds() throws Exception {
        ToolRegistry reg = new ToolRegistry(List.of(fakeSearch()));
        AgentProperties props = new AgentProperties();
        props.setMaxRounds(5); // high limit — proves early abort, not just hitting the cap

        // Simulate client disconnect: every emitter.send() throws
        doThrow(new IOException("client gone")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        // client.chat() always returns a tool_call (never converges on its own)
        when(client.chat(anyList(), anyList()))
                .thenReturn(new AgentTurn(null, List.of(new ToolCall("c1", "search_products", "{}"))));

        AssistantAgentServiceImpl svc =
                new AssistantAgentServiceImpl(client, reg, props, new ObjectMapper(), Runnable::run,
                        new AiMetrics(new SimpleMeterRegistry()));
        svc.chat("耳机", new AgentContext(1L, "CUSTOMER"), emitter);

        // The first emitter.send() (step event in executeTool) sets disconnected=true;
        // round-0 completes its tool calls then the next iteration checks the flag and breaks.
        // Pre-fix code would loop all 5 rounds (chat called 5 times).
        verify(client, times(1)).chat(anyList(), anyList());
        // streamFinal must never be called — client is gone
        verify(client, never()).streamFinal(anyList(), any());
        // complete() called exactly once (disconnected emitter swallows it harmlessly)
        verify(emitter, times(1)).complete();
    }

    @Test
    void disconnect_doesNotLeakAcrossRequests() throws Exception {
        ToolRegistry reg = new ToolRegistry(List.of(fakeSearch()));
        AgentProperties props = new AgentProperties();
        props.setMaxRounds(5);

        // --- Request 1: emitter that always throws (simulates disconnect) ---
        SseEmitter emitter1 = mock(SseEmitter.class);
        doThrow(new java.io.IOException("client gone")).when(emitter1).send(any(SseEmitter.SseEventBuilder.class));

        // client always returns tool_call — never converges on its own
        when(client.chat(anyList(), anyList()))
                .thenReturn(new AgentTurn(null, List.of(new ToolCall("c1", "search_products", "{}"))));

        AssistantAgentServiceImpl svc =
                new AssistantAgentServiceImpl(client, reg, props, new ObjectMapper(), Runnable::run,
                        new AiMetrics(new SimpleMeterRegistry()));

        svc.chat("request1", new AgentContext(1L, "CUSTOMER"), emitter1);

        // Disconnect on round-0's first send sets flag; round-1 guard breaks immediately.
        verify(client, times(1)).chat(anyList(), anyList());
        verify(client, never()).streamFinal(anyList(), any());

        // --- Request 2: healthy emitter on the SAME service instance ---
        SseEmitter emitter2 = mock(SseEmitter.class);
        // emitter2 does NOT throw — default Mockito void-method behaviour

        reset(client);
        when(client.chat(anyList(), anyList()))
                .thenReturn(new AgentTurn(null, List.of(new ToolCall("c2", "search_products", "{}"))))
                .thenReturn(new AgentTurn("回答", List.of()));
        doAnswer(inv -> {
            Consumer<String> cb = inv.getArgument(1);
            cb.accept("回答");
            return null;
        }).when(client).streamFinal(anyList(), any());

        svc.chat("request2", new AgentContext(1L, "CUSTOMER"), emitter2);

        // If the disconnect flag leaked from request 1, disconnected.get() would be true at the
        // start of round-0, the loop would break immediately, streamFinal would never be called,
        // and the assertions below would FAIL.
        verify(client, times(1)).streamFinal(anyList(), any());
        verify(emitter2, times(1)).complete();
    }

    @Test
    void toolThrows_feedsErrorResultButDoesNotCrash() throws Exception {
        Tool boom = new Tool() {
            public String name() { return "search_products"; }
            public String description() { return "搜"; }
            public java.util.Map<String,Object> parametersSchema() { return java.util.Map.of("type","object","properties",java.util.Map.of()); }
            public Object execute(com.fasterxml.jackson.databind.JsonNode a, AgentContext c) { throw new RuntimeException("boom"); }
        };
        ToolRegistry reg = new ToolRegistry(List.of(boom));
        when(client.chat(anyList(), anyList()))
                .thenReturn(new AgentTurn(null, List.of(new ToolCall("c","search_products","{}"))))
                .thenReturn(new AgentTurn("已尽力", List.of()));
        doAnswer(inv -> { Consumer<String> cb = inv.getArgument(1); cb.accept("已尽力"); return null; })
                .when(client).streamFinal(anyList(), any());

        service(reg).chat("耳机", new AgentContext(1L,"CUSTOMER"), emitter);

        // loop continued to convergence despite tool throw — streamFinal reached
        verify(client, times(1)).streamFinal(anyList(), any());
        // completed gracefully (no exception propagated)
        verify(emitter).complete();
        // chat() called exactly twice: round-0 tool call + round-1 final text
        verify(client, times(2)).chat(anyList(), anyList());
    }
}
