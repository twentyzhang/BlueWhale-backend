package com.twentyzhang.bluewhale.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PaymentSchedulerConfig {

    /** 支付回调延迟调度器（模拟异步回调）。命名以便 @Qualifier 注入，避免与 WS 心跳调度器歧义。 */
    @Bean
    public TaskScheduler paymentCallbackScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("pay-callback-");
        scheduler.initialize();
        return scheduler;
    }
}
