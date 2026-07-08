package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.service.llm.AgentMessage;
import com.twentyzhang.bluewhale.service.llm.AgentTurn;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** tools-aware 对话补全（可插拔：现 qwen OpenAI 兼容）。 */
public interface AgentChatClient {

    /** 非流式一轮：带工具 schema 调 LLM，返回工具调用或最终文本。toolSchemas 为 OpenAI tools 数组元素列表。 */
    AgentTurn chat(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas);

    /** 流式最终回答：强制 tool_choice=none，仅产出文本，逐段回调 onDelta。 */
    void streamFinal(List<AgentMessage> messages, Consumer<String> onDelta);
}
