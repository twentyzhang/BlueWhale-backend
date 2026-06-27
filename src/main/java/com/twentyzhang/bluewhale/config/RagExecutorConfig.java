package com.twentyzhang.bluewhale.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class RagExecutorConfig {

    /** RAG 流式生成的后台线程池：SseEmitter 主线程秒返回，流在此推送，不阻塞容器请求线程。 */
    @Bean("ragStreamExecutor")
    public Executor ragStreamExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(8);
        e.setQueueCapacity(50);
        e.setThreadNamePrefix("rag-stream-");
        e.initialize();
        return e;
    }
}
