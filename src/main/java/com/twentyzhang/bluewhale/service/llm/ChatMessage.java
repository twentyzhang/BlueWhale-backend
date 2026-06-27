package com.twentyzhang.bluewhale.service.llm;

/** LLM 对话消息（OpenAI 风格 role/content）。 */
public record ChatMessage(String role, String content) {}
