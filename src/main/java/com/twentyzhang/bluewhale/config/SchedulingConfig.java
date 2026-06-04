package com.twentyzhang.bluewhale.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 定时任务支持。
 * 集中在此处启用，定时任务类（如 {@code OrderAutocancelJob}）只需声明 {@code @Scheduled}。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
