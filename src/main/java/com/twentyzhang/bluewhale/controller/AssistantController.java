package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.config.AgentProperties;
import com.twentyzhang.bluewhale.service.AssistantAgentService;
import com.twentyzhang.bluewhale.service.tool.AgentContext;
import com.twentyzhang.bluewhale.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantAgentService agentService;
    private final AgentProperties props;

    /** AI 导购 Agent（需登录，SSE 流式）。 */
    @GetMapping(value = "/assistant/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam String q) {
        AuthUser user = AuthUtil.getCurrentUser();              // 入口捕获身份（未登录抛 401）
        SseEmitter emitter = new SseEmitter(props.getEmitterTimeoutMs());
        agentService.chat(q, new AgentContext(user.userId(), user.role()), emitter);
        return emitter;
    }
}
