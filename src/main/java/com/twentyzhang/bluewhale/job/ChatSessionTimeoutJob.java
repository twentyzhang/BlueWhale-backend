package com.twentyzhang.bluewhale.job;

import com.twentyzhang.bluewhale.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 接待会话超时自动释放任务（第二轮 B1）。
 *
 * <p>每 5 分钟扫描所有已接入会话，归属客服当前不在线者自动释放回公共池，避免买家长期挂在失联客服名下。
 * 5 分钟扫描间隔本身即宽限期：短暂刷新/重连的客服在扫描时已重新在线，不会被误释放。
 * 系统级调用，不读 SecurityContext（同 {@code rebuildAll} 教训）。
 *
 * <p>{@code @EnableScheduling} 已在 {@link OrderAutocancelJob} 上全局开启，此处不重复添加。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionTimeoutJob {

    private final ChatService chatService;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void releaseOfflineAssignees() {
        try {
            int released = chatService.autoReleaseOfflineAssignees();
            if (released > 0) {
                log.info("接待超时自动释放完成：释放 {} 个失联客服的会话", released);
            }
        } catch (Exception e) {
            log.error("接待超时自动释放任务失败", e);
        }
    }
}
