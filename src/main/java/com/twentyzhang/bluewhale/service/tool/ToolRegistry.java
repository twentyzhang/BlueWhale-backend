package com.twentyzhang.bluewhale.service.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolRegistry {

    private final Map<String, Tool> byName = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> tools) {
        for (Tool t : tools) byName.put(t.name(), t);
    }

    public Tool get(String name) {
        Tool t = byName.get(name);
        if (t == null) throw new IllegalArgumentException("未知工具：" + name);
        return t;
    }

    /** 汇总所有工具为 OpenAI tools 数组元素。 */
    public List<Map<String, Object>> toolSchemas() {
        return byName.values().stream().map(t -> Map.<String, Object>of(
                "type", "function",
                "function", Map.of(
                        "name", t.name(),
                        "description", t.description(),
                        "parameters", t.parametersSchema()))).toList();
    }
}
