package com.twentyzhang.bluewhale.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AgentExecutorConfig {

    private final MdcTaskDecorator mdcTaskDecorator;

    public AgentExecutorConfig(MdcTaskDecorator mdcTaskDecorator) {
        this.mdcTaskDecorator = mdcTaskDecorator;
    }

    /** Agent 流式推送后台线程池：SseEmitter 主线程秒返回，Agent 循环在此运行，不阻塞容器请求线程。 */
    @Bean("assistantStreamExecutor")
    public Executor assistantStreamExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(8);
        e.setQueueCapacity(50);
        e.setThreadNamePrefix("agent-stream-");
        e.setTaskDecorator(mdcTaskDecorator);
        e.initialize();
        return e;
    }
}
