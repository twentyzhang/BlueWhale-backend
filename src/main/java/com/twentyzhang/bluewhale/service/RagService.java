package com.twentyzhang.bluewhale.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RagService {

    /** 处理一次导购问答：检索→（空短路）→构造 prompt→后台流式生成，事件推到 emitter。 */
    void answer(String q, int topK, SseEmitter emitter);
}
