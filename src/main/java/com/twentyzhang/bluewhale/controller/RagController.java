package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.config.RagProperties;
import com.twentyzhang.bluewhale.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final RagProperties props;

    /** AI 导购问答（开放，SSE 流式）。topK 不传用配置默认值。 */
    @GetMapping(value = "/products/qa", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter qa(@RequestParam String q,
                         @RequestParam(required = false) Integer topK) {
        SseEmitter emitter = new SseEmitter(props.getEmitterTimeoutMs());
        int k = topK != null ? topK : props.getTopK();
        ragService.answer(q, k, emitter);
        return emitter;
    }
}
