package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.service.llm.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

/** 流式对话补全（可插拔：现 qwen，OpenAI 兼容；换厂商仅改实现/配置）。 */
public interface ChatCompletionClient {

    /** 把 messages 发给 LLM，每收到一段增量文本调一次 onDelta；正常结束返回，出错抛异常。 */
    void streamChat(List<ChatMessage> messages, Consumer<String> onDelta);
}
