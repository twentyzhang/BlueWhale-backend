package com.twentyzhang.bluewhale.service.llm;

import java.util.List;

/** Agent 对话消息（OpenAI 风格，支持 assistant tool_calls 与 tool 结果）。 */
public record AgentMessage(String role, String content,
                           List<ToolCall> toolCalls, String toolCallId) {
    public static AgentMessage system(String c) { return new AgentMessage("system", c, null, null); }
    public static AgentMessage user(String c)   { return new AgentMessage("user", c, null, null); }
    public static AgentMessage assistant(String c) { return new AgentMessage("assistant", c, null, null); }
    public static AgentMessage assistantToolCalls(List<ToolCall> calls) {
        return new AgentMessage("assistant", null, calls, null);
    }
    public static AgentMessage tool(String toolCallId, String resultJson) {
        return new AgentMessage("tool", resultJson, null, toolCallId);
    }
}
