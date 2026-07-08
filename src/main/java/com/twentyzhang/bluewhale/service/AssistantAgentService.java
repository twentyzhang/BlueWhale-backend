package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.service.tool.AgentContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AssistantAgentService {
    /** 运行 Agent 循环并通过 SSE 推送 step/tool/products/answer/done/error 事件。 */
    void chat(String q, AgentContext ctx, SseEmitter emitter);
}
