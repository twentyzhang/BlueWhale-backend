package com.twentyzhang.bluewhale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.config.AgentProperties;
import com.twentyzhang.bluewhale.service.impl.AssistantAgentServiceImpl;
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

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssistantAgentServiceTest {

    @Mock AgentChatClient client;
    @Mock SseEmitter emitter;

    private AssistantAgentServiceImpl service(ToolRegistry reg) {
        return new AssistantAgentServiceImpl(client, reg, new AgentProperties(), new ObjectMapper(), Runnable::run);
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
                new AssistantAgentServiceImpl(client, reg, props, new ObjectMapper(), Runnable::run);

        svc.chat("耳机", new AgentContext(1L,"CUSTOMER"), emitter);

        // streamFinal must NEVER be called — proves the loop didn't converge
        verify(client, never()).streamFinal(anyList(), any());
        // emitter must complete — proves the loop terminated (not hung)
        verify(emitter).complete();
        // chat() called exactly maxRounds times — proves the loop ran to the limit, not fewer
        verify(client, times(2)).chat(anyList(), anyList());
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
