// AgentMessageTest.java
package com.twentyzhang.bluewhale.service.llm;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgentMessageTest {
    @Test
    void factories_buildExpectedRolesAndFields() {
        assertEquals("system", AgentMessage.system("s").role());
        assertEquals("user", AgentMessage.user("u").role());

        ToolCall tc = new ToolCall("call_1", "search_products", "{\"q\":\"耳机\"}");
        AgentMessage asst = AgentMessage.assistantToolCalls(List.of(tc));
        assertEquals("assistant", asst.role());
        assertEquals(1, asst.toolCalls().size());
        assertEquals("search_products", asst.toolCalls().get(0).name());

        AgentMessage tool = AgentMessage.tool("call_1", "[]");
        assertEquals("tool", tool.role());
        assertEquals("call_1", tool.toolCallId());
        assertEquals("[]", tool.content());
    }

    @Test
    void agentTurn_hasToolCalls_reflectsPresence() {
        assertTrue(new AgentTurn(null, List.of(new ToolCall("c","n","{}"))). hasToolCalls());
        assertFalse(new AgentTurn("done", List.of()).hasToolCalls());
        assertFalse(new AgentTurn("done", null).hasToolCalls());
    }
}
