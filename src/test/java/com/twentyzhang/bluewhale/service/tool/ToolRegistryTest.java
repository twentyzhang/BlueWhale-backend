// ToolRegistryTest.java
package com.twentyzhang.bluewhale.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    static class FakeTool implements Tool {
        public String name() { return "fake"; }
        public String description() { return "测试工具"; }
        public Map<String,Object> parametersSchema() { return Map.of("type","object","properties",Map.of()); }
        public Object execute(JsonNode args, AgentContext ctx) { return "ok"; }
    }

    @Test
    void get_returnsRegisteredTool_andThrowsOnUnknown() {
        ToolRegistry reg = new ToolRegistry(List.of(new FakeTool()));
        assertEquals("fake", reg.get("fake").name());
        assertThrows(IllegalArgumentException.class, () -> reg.get("nope"));
    }

    @Test
    void toolSchemas_wrapsEachToolInOpenAiFunctionFormat() {
        ToolRegistry reg = new ToolRegistry(List.of(new FakeTool()));
        List<Map<String,Object>> schemas = reg.toolSchemas();
        assertEquals(1, schemas.size());
        assertEquals("function", schemas.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String,Object> fn = (Map<String,Object>) schemas.get(0).get("function");
        assertEquals("fake", fn.get("name"));
        assertEquals("测试工具", fn.get("description"));
        assertNotNull(fn.get("parameters"));
    }
}
