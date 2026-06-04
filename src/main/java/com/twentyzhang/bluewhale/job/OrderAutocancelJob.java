package com.twentyzhang.bluewhale.job;

import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 未支付订单自动取消定时任务。
 *
 * <p>每分钟扫描一次，将创建时间早于「当前时间 − {@code bluewhale.order.auto-cancel-days} 天」
 * 且仍为 PENDING_PAYMENT 的订单取消（恢复库存与优惠券，详见
 * {@link OrderService#cancelExpiredUnpaidOrder(Order)}）。
 *
 * <p>逐条独立处理：单条订单取消失败仅记录错误日志，不影响其余订单。
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OrderAutocancelJob {

    private final OrderMapper   orderMapper;
    private final OrderService  orderService;

    /** 未支付订单保留天数，超过即自动取消。从配置读取，不硬编码。 */
    @Value("${bluewhale.order.auto-cancel-days}")
    private int autoCancelDays;

    /** 每 60 秒执行一次（上次执行结束后再隔 60 秒）。 */
    @Scheduled(fixedDelay = 60000)
    public void autoCancelExpiredOrders() {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(autoCancelDays);
        List<Order> expiredOrders = orderMapper.selectExpiredUnpaidOrders(expireTime);

        int cancelledCount = 0;
        for (Order order : expiredOrders) {
            try {
                orderService.cancelExpiredUnpaidOrder(order);
                cancelledCount++;
            } catch (Exception e) {
                // 单条失败不影响其他订单，仅记录错误日志
                log.error("自动取消订单失败，orderId={}", order.getId(), e);
            }
        }

        log.info("自动取消过期订单完成，本次取消 {} 条", cancelledCount);
    }
}
