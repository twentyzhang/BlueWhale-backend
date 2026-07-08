package com.twentyzhang.bluewhale.service.llm;

import java.util.List;

/** 一轮 LLM 输出：要么要调工具（toolCalls），要么给最终文本（finalText）。 */
public record AgentTurn(String finalText, List<ToolCall> toolCalls) {
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
}
