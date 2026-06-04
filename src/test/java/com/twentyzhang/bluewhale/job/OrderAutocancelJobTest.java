package com.twentyzhang.bluewhale.job;

import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("OrderAutocancelJob")
@ExtendWith(MockitoExtension.class)
class OrderAutocancelJobTest {

    @Mock private OrderMapper  orderMapper;
    @Mock private OrderService orderService;

    @InjectMocks
    private OrderAutocancelJob job;

    @Test
    @DisplayName("逐条处理：单条订单取消失败不影响其余订单全部被尝试")
    void singleFailure_doesNotAbortOthers() {
        ReflectionTestUtils.setField(job, "autoCancelDays", 14);
        when(orderMapper.selectExpiredUnpaidOrders(any())).thenReturn(List.of(
                Order.builder().id(1L).build(),
                Order.builder().id(2L).build(),
                Order.builder().id(3L).build()));
        // 第 2 条抛异常，第 1、3 条正常
        doAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == 2L) throw new RuntimeException("boom");
            return null;
        }).when(orderService).cancelExpiredUnpaidOrder(any());

        job.autoCancelExpiredOrders();

        // 三条都被尝试取消（异常被 try-catch 隔离，未中断循环）
        verify(orderService, times(3)).cancelExpiredUnpaidOrder(any());
    }

    @Test
    @DisplayName("按配置天数计算过期时间并查询")
    void queriesWithConfiguredExpireTime() {
        ReflectionTestUtils.setField(job, "autoCancelDays", 14);
        when(orderMapper.selectExpiredUnpaidOrders(any())).thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusDays(14);
        job.autoCancelExpiredOrders();
        LocalDateTime after = LocalDateTime.now().minusDays(14);

        // 验证传入的 expireTime 落在 [before, after] 区间（约等于 now - 14 天）
        verify(orderMapper).selectExpiredUnpaidOrders(argThat(t ->
                !t.isBefore(before) && !t.isAfter(after)));
    }
}
