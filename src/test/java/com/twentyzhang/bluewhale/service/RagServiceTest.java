package com.twentyzhang.bluewhale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.config.RagProperties;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.service.impl.RagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagService")
class RagServiceTest {

    @Mock SemanticSearchService semanticSearchService;
    @Mock ChatCompletionClient chatClient;
    @Mock SseEmitter emitter;

    RagServiceImpl service;

    @BeforeEach
    void setUp() {
        RagProperties props = new RagProperties();
        service = new RagServiceImpl(
                semanticSearchService, chatClient,
                new RagPromptBuilder(props),
                Runnable::run,                 // 同步 executor
                new ObjectMapper());
    }

    private static ProductListItemResponse product(String name) {
        return ProductListItemResponse.builder()
                .id(1L).name(name).categoryName("饮料").price(new BigDecimal("9.90")).build();
    }

    @Test
    @DisplayName("正常路径：先 products，后多次 answer，最后 done；complete 一次")
    void answer_streamsProductsThenAnswerThenDone() throws Exception {
        when(semanticSearchService.search("耳机", null, null, null, 6))
                .thenReturn(List.of(product("气泡水")));
        doAnswer(inv -> {
            Consumer<String> cb = inv.getArgument(1);
            cb.accept("你好");
            cb.accept("，推荐气泡水");
            return null;
        }).when(chatClient).streamChat(anyList(), any());

        service.answer("耳机", 6, emitter);

        // products(1) + answer(2) + done(1) = 4 次 send
        verify(emitter, times(4)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, times(1)).complete();
        // products 事件在 streamChat 之前
        var inOrder = inOrder(emitter, chatClient);
        inOrder.verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        inOrder.verify(chatClient).streamChat(anyList(), any());
    }

    @Test
    @DisplayName("检索为空：短路不调 chatClient，推 products+answer+done")
    void answer_emptyShortCircuits() throws Exception {
        when(semanticSearchService.search(anyString(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.answer("不存在的东西", 6, emitter);

        verify(emitter, times(3)).send(any(SseEmitter.SseEventBuilder.class)); // products+answer+done
        verify(emitter).complete();
        verify(chatClient, never()).streamChat(anyList(), any());
    }

    @Test
    @DisplayName("生成异常：products 已推 + error；complete 一次")
    void answer_generationFails_pushesError() throws Exception {
        when(semanticSearchService.search(anyString(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(product("气泡水")));
        doThrow(new RuntimeException("qwen down")).when(chatClient).streamChat(anyList(), any());

        service.answer("耳机", 6, emitter);

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class)); // products + error
        verify(emitter).complete();
    }

    @Test
    @DisplayName("检索异常：推 error，不推 products、不调 chatClient")
    void answer_retrievalFails_pushesError() throws Exception {
        when(semanticSearchService.search(anyString(), any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("qdrant down"));

        service.answer("耳机", 6, emitter);

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class)); // 仅 error
        verify(emitter).complete();
        verify(chatClient, never()).streamChat(anyList(), any());
    }
}
