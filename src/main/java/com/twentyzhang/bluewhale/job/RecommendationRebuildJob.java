package com.twentyzhang.bluewhale.job;

import com.twentyzhang.bluewhale.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 推荐相似度离线重建任务（路线图任务 8）。
 *
 * <p>每天凌晨 3:00 全量重建 product_similarity（相似度为慢变量，低峰期重算即可）。
 * 直接调用 {@link RecommendationService#rebuildAll()}，不读 SecurityContext（调度线程无 context）。
 * 整体 try-catch：重建失败仅记日志，不影响应用。
 *
 * <p>{@code @EnableScheduling} 已在 {@link OrderAutocancelJob} 上全局开启，此处不重复添加。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationRebuildJob {

    private final RecommendationService recommendationService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void rebuild() {
        try {
            int written = recommendationService.rebuildAll();
            log.info("推荐相似度定时重建完成，写入 {} 条", written);
        } catch (Exception e) {
            log.error("推荐相似度定时重建失败", e);
        }
    }
}
