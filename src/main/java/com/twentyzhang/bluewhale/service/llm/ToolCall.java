package com.twentyzhang.bluewhale.service.llm;

/** 一次工具调用请求（LLM 产生）。argumentsJson 为 JSON 字符串。 */
public record ToolCall(String id, String name, String argumentsJson) {}
