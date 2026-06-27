package com.twentyzhang.bluewhale.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.service.ChatCompletionClient;
import com.twentyzhang.bluewhale.service.RagPromptBuilder;
import com.twentyzhang.bluewhale.service.RagService;
import com.twentyzhang.bluewhale.service.SemanticSearchService;
import com.twentyzhang.bluewhale.service.llm.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private final SemanticSearchService semanticSearchService;
    private final ChatCompletionClient chatClient;
    private final RagPromptBuilder promptBuilder;
    private final Executor ragStreamExecutor;
    private final ObjectMapper objectMapper;

    public RagServiceImpl(SemanticSearchService semanticSearchService,
                          ChatCompletionClient chatClient,
                          RagPromptBuilder promptBuilder,
                          @Qualifier("ragStreamExecutor") Executor ragStreamExecutor,
                          ObjectMapper objectMapper) {
        this.semanticSearchService = semanticSearchService;
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
        this.ragStreamExecutor = ragStreamExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public void answer(String q, int topK, SseEmitter emitter) {
        List<ProductListItemResponse> products;
        try {
            products = semanticSearchService.search(q, null, null, null, topK);
        } catch (Exception e) {
            log.warn("RAG 检索失败：{}", e.getMessage());
            sendError(emitter, "检索失败，请稍后再试");
            return;
        }

        if (!sendProducts(emitter, products)) {
            return; // 客户端已断开
        }

        if (products.isEmpty()) {
            sendAnswer(emitter, "没找到相关商品，换个说法试试？");
            sendDone(emitter);
            return;
        }

        List<ChatMessage> messages = promptBuilder.build(q, products);
        ragStreamExecutor.execute(() -> {
            try {
                chatClient.streamChat(messages, delta -> sendAnswer(emitter, delta));
                sendDone(emitter);
            } catch (Exception e) {
                log.warn("RAG 生成失败：{}", e.getMessage());
                sendError(emitter, "生成繁忙，请稍后再试");
            }
        });
    }

    /** 推商品卡事件；失败（客户端断开）返回 false。 */
    private boolean sendProducts(SseEmitter emitter, List<ProductListItemResponse> products) {
        try {
            String json = objectMapper.writeValueAsString(products);
            emitter.send(SseEmitter.event().name("products").data(json, MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception e) {
            emitter.completeWithError(e);
            return false;
        }
    }

    private void sendAnswer(SseEmitter emitter, String text) {
        try {
            emitter.send(SseEmitter.event().name("answer").data(text));
        } catch (Exception e) {
            // 客户端断开：忽略后续推送
        }
    }

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("done").data(""));
        } catch (Exception ignored) {
        }
        emitter.complete();
    }

    private void sendError(SseEmitter emitter, String msg) {
        try {
            emitter.send(SseEmitter.event().name("error").data(msg));
        } catch (Exception ignored) {
        }
        emitter.complete();
    }
}
