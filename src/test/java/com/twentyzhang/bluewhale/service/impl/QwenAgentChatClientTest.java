package com.twentyzhang.bluewhale.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.config.RagProperties;
import com.twentyzhang.bluewhale.service.llm.AgentMessage;
import com.twentyzhang.bluewhale.service.llm.AgentTurn;
import com.twentyzhang.bluewhale.service.llm.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QwenAgentChatClientTest {

    QwenAgentChatClient client;

    @BeforeEach
    void setUp() { client = new QwenAgentChatClient(new RagProperties(), new ObjectMapper()); }

    @Test
    void parseTurn_withToolCalls_returnsToolCalls() {
        String json = """
            {"choices":[{"message":{"role":"assistant","tool_calls":[
              {"id":"call_1","type":"function","function":{"name":"search_products","arguments":"{\\"q\\":\\"耳机\\"}"}}
            ]}}]}""";
        AgentTurn turn = client.parseTurn(json);
        assertTrue(turn.hasToolCalls());
        assertEquals(1, turn.toolCalls().size());
        ToolCall c = turn.toolCalls().get(0);
        assertEquals("call_1", c.id());
        assertEquals("search_products", c.name());
        assertTrue(c.argumentsJson().contains("耳机"));
    }

    @Test
    void parseTurn_withContent_returnsFinalText() {
        String json = """
            {"choices":[{"message":{"role":"assistant","content":"给你推荐这款耳机"}}]}""";
        AgentTurn turn = client.parseTurn(json);
        assertFalse(turn.hasToolCalls());
        assertEquals("给你推荐这款耳机", turn.finalText());
    }

    @Test
    void toWireMessages_serializesAssistantToolCallsAndToolResult() {
        var msgs = List.of(
            AgentMessage.user("你好"),
            AgentMessage.assistantToolCalls(List.of(new ToolCall("call_1","search_products","{\"q\":\"耳机\"}"))),
            AgentMessage.tool("call_1", "[{\"id\":1}]"));
        List<Map<String,Object>> wire = client.toWireMessages(msgs);
        assertEquals("user", wire.get(0).get("role"));
        assertEquals("assistant", wire.get(1).get("role"));
        assertTrue(wire.get(1).containsKey("tool_calls"));
        assertEquals("tool", wire.get(2).get("role"));
        assertEquals("call_1", wire.get(2).get("tool_call_id"));
    }
}
