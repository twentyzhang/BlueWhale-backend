package com.twentyzhang.bluewhale.service.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.dto.OrderListItemResponse;
import com.twentyzhang.bluewhale.service.CouponService;
import com.twentyzhang.bluewhale.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalToolsTest {

    final ObjectMapper om = new ObjectMapper();
    @Mock OrderService orderService;
    @Mock CouponService couponService;

    @Test
    void myOrders_alwaysUsesCtxUserId_ignoringArgsUserId() throws Exception {
        Page<OrderListItemResponse> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(orderService.getMyOrders(eq(7L), any(), anyInt(), anyInt())).thenReturn(page);

        MyOrdersTool tool = new MyOrdersTool(orderService);
        assertTrue(tool.description().contains("必须只基于当前用户上下文"));
        assertTrue(tool.description().contains("上次买过什么"));
        // 恶意 args 里塞别人的 userId=999，必须被忽略
        tool.execute(om.readTree("{\"userId\":999,\"status\":\"PAID\"}"), new AgentContext(7L, "CUSTOMER"));

        verify(orderService).getMyOrders(eq(7L), eq("PAID"), anyInt(), anyInt());
        verify(orderService, never()).getMyOrders(eq(999L), any(), anyInt(), anyInt());
    }

    @Test
    void myCoupons_alwaysUsesCtxUserId_ignoringArgsUserId() throws Exception {
        when(couponService.getMyCoupons(eq(7L), isNull())).thenReturn(List.of());
        MyCouponsTool tool = new MyCouponsTool(couponService);
        assertTrue(tool.description().contains("必须只基于当前用户上下文"));
        assertTrue(tool.description().contains("我的券能不能用"));
        tool.execute(om.readTree("{\"userId\":999}"), new AgentContext(7L, "CUSTOMER"));
        verify(couponService).getMyCoupons(7L, null);
        verify(couponService, never()).getMyCoupons(eq(999L), any());
    }
}
