package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.GlobalOrderReportResponse;
import com.twentyzhang.bluewhale.dto.StoreBreakdownResponse;
import com.twentyzhang.bluewhale.dto.StoreOrderReportResponse;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.mapper.StoreMapper;
import com.twentyzhang.bluewhale.service.impl.ReportServiceImpl;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.CacheUtil;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ReportService")
class ReportServiceTest extends BaseServiceTest {

    // OrderMapper 作为 baseMapper 并提供报表聚合方法
    @Mock private OrderMapper orderMapper;
    // StoreMapper：ReportServiceImpl 并不依赖它（storeBreakdown 由 OrderMapper.selectStoreBreakdown 的 JOIN 产出），
    // 按题目要求声明，但不参与注入，仅作占位。
    @Mock private StoreMapper storeMapper;
    @Mock private CacheUtil cacheUtil;

    @InjectMocks
    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reportService, "baseMapper", orderMapper);
        // CacheUtil 透传：报表用 Class 重载，loader 返回非 null，直接执行回源
        lenient().when(cacheUtil.getOrLoad(anyString(), anyLong(), anyLong(),
                        any(Supplier.class), any(Class.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    // ── 聚合结果行构造辅助（Mapper 返回 List<Map<String,Object>>） ────────────────

    private static Map<String, Object> statusRow(String status, int cnt) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("cnt", cnt);
        return m;
    }

    private static Map<String, Object> dailyRow(String date, String revenue, int orders) {
        Map<String, Object> m = new HashMap<>();
        m.put("date", date);
        m.put("revenue", new BigDecimal(revenue));
        m.put("orders", orders);
        return m;
    }

    private static Map<String, Object> storeRow(long storeId, String storeName, int orders, String revenue) {
        Map<String, Object> m = new HashMap<>();
        m.put("storeId", storeId);
        m.put("storeName", storeName);
        m.put("orders", orders);
        m.put("revenue", new BigDecimal(revenue));
        return m;
    }

    // ── 4. getStoreOrderReport ────────────────────────────────────────────────

    @Nested
    @DisplayName("getStoreOrderReport")
    class GetStoreOrderReportTests {

        @Test
        @DisplayName("正常返回报表，totalOrders 含全状态、totalRevenue 仅统计 COMPLETED")
        void success_revenueOnlyCompleted() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);
            // 全状态统计：100 + 15 + 5 + 8 = 128（含已取消/待支付）
            when(orderMapper.selectStatusBreakdown(eq(100L), any(), any())).thenReturn(List.of(
                    statusRow("COMPLETED", 100),
                    statusRow("CANCELLED", 15),
                    statusRow("PENDING_PAYMENT", 5),
                    statusRow("SHIPPED", 8)));
            // 按日收入：SQL 已过滤 status='COMPLETED'，合计 560 + 480 = 1040
            when(orderMapper.selectDailyRevenue(eq(100L), any(), any())).thenReturn(List.of(
                    dailyRow("2026-05-19", "560.00", 18),
                    dailyRow("2026-05-20", "480.00", 15)));

            StoreOrderReportResponse resp = reportService.getStoreOrderReport(100L, null, null);

            assertEquals(128, resp.getTotalOrders());
            // 收入只来自 COMPLETED（已取消的 15 单不贡献收入）
            assertEquals(new BigDecimal("1040.00"), resp.getTotalRevenue());
            assertEquals(100, resp.getStatusBreakdown().get("COMPLETED"));
            assertEquals(15,  resp.getStatusBreakdown().get("CANCELLED"));
            assertEquals(2, resp.getDailyRevenue().size());
            assertEquals("2026-05-19", resp.getDailyRevenue().get(0).getDate());
            assertEquals(new BigDecimal("560.00"), resp.getDailyRevenue().get(0).getRevenue());
        }

        @Test
        @DisplayName("averageOrderAmount = totalRevenue / totalOrders，2 位 HALF_UP")
        void averageOrderAmount_rounding() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);
            when(orderMapper.selectStatusBreakdown(eq(100L), any(), any()))
                    .thenReturn(List.of(statusRow("COMPLETED", 3)));
            when(orderMapper.selectDailyRevenue(eq(100L), any(), any()))
                    .thenReturn(List.of(dailyRow("2026-05-20", "1000.00", 3)));

            StoreOrderReportResponse resp = reportService.getStoreOrderReport(100L, null, null);

            assertEquals(3, resp.getTotalOrders());
            assertEquals(new BigDecimal("1000.00"), resp.getTotalRevenue());
            // 1000.00 / 3 = 333.333... → 333.33
            assertEquals(new BigDecimal("333.33"), resp.getAverageOrderAmount());
        }

        @Test
        @DisplayName("totalOrders 为 0 时 averageOrderAmount 返回 0，不抛除零异常")
        void zeroOrders_avgZero() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);
            when(orderMapper.selectStatusBreakdown(eq(100L), any(), any())).thenReturn(List.of());
            when(orderMapper.selectDailyRevenue(eq(100L), any(), any())).thenReturn(List.of());

            StoreOrderReportResponse resp = reportService.getStoreOrderReport(100L, null, null);

            assertEquals(0, resp.getTotalOrders());
            assertEquals(BigDecimal.ZERO, resp.getAverageOrderAmount());
            assertEquals(BigDecimal.ZERO, resp.getTotalRevenue());
            assertTrue(resp.getDailyRevenue().isEmpty());
        }

        @Test
        @DisplayName("startDate 晚于 endDate 时抛出 BusinessException")
        void startAfterEnd_throws() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reportService.getStoreOrderReport(
                            100L, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1)));

            assertEquals(Result.CODE_BAD_REQUEST, ex.getCode());
            verify(orderMapper, never()).selectStatusBreakdown(any(), any(), any());
        }

        @Test
        @DisplayName("不传日期时默认查询最近 30 天")
        void defaultLast30Days() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);
            when(orderMapper.selectStatusBreakdown(eq(100L), any(), any())).thenReturn(List.of());
            when(orderMapper.selectDailyRevenue(eq(100L), any(), any())).thenReturn(List.of());

            reportService.getStoreOrderReport(100L, null, null);

            ArgumentCaptor<LocalDate> startCap = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> endCap   = ArgumentCaptor.forClass(LocalDate.class);
            verify(orderMapper).selectStatusBreakdown(eq(100L), startCap.capture(), endCap.capture());

            LocalDate today = LocalDate.now();
            assertEquals(today, endCap.getValue());
            assertEquals(today.minusDays(30), startCap.getValue());
        }

        @Test
        @DisplayName("非 Staff 调用时抛出 BusinessException（code 403）")
        void nonStaff_throws403() {
            mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reportService.getStoreOrderReport(100L, null, null));

            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verify(orderMapper, never()).selectStatusBreakdown(any(), any(), any());
        }
    }

    // ── 5. getGlobalOrderReport ───────────────────────────────────────────────

    @Nested
    @DisplayName("getGlobalOrderReport")
    class GetGlobalOrderReportTests {

        @Test
        @DisplayName("正常返回全局报表，storeBreakdown 包含所有商店数据")
        void success_storeBreakdown() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);
            // 全局状态统计（storeId 传 null）：500 + 360 = 860
            when(orderMapper.selectStatusBreakdown(isNull(), any(), any())).thenReturn(List.of(
                    statusRow("COMPLETED", 500),
                    statusRow("CANCELLED", 360)));
            when(orderMapper.selectDailyRevenue(isNull(), any(), any())).thenReturn(List.of(
                    dailyRow("2026-05-20", "1200.00", 40)));
            when(orderMapper.selectStoreBreakdown(any(), any())).thenReturn(List.of(
                    storeRow(1L, "南鲸旗舰店", 128, "3840.50"),
                    storeRow(2L, "味全食品店", 50, "2000.00")));

            GlobalOrderReportResponse resp = reportService.getGlobalOrderReport(null, null);

            assertEquals(860, resp.getTotalOrders());
            assertEquals(new BigDecimal("1200.00"), resp.getTotalRevenue());
            assertEquals(1, resp.getDailyRevenue().size());

            assertEquals(2, resp.getStoreBreakdown().size());
            StoreBreakdownResponse store1 = findStore(resp.getStoreBreakdown(), 1L);
            assertEquals("南鲸旗舰店", store1.getStoreName());
            assertEquals(128, store1.getOrders());
            assertEquals(new BigDecimal("3840.50"), store1.getRevenue());
            StoreBreakdownResponse store2 = findStore(resp.getStoreBreakdown(), 2L);
            assertEquals("味全食品店", store2.getStoreName());
            assertEquals(new BigDecimal("2000.00"), store2.getRevenue());
        }

        @Test
        @DisplayName("不传日期时默认查询最近 30 天")
        void defaultLast30Days() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);
            when(orderMapper.selectStatusBreakdown(isNull(), any(), any())).thenReturn(List.of());
            when(orderMapper.selectDailyRevenue(isNull(), any(), any())).thenReturn(List.of());
            when(orderMapper.selectStoreBreakdown(any(), any())).thenReturn(List.of());

            reportService.getGlobalOrderReport(null, null);

            ArgumentCaptor<LocalDate> startCap = ArgumentCaptor.forClass(LocalDate.class);
            ArgumentCaptor<LocalDate> endCap   = ArgumentCaptor.forClass(LocalDate.class);
            verify(orderMapper).selectStatusBreakdown(isNull(), startCap.capture(), endCap.capture());

            LocalDate today = LocalDate.now();
            assertEquals(today, endCap.getValue());
            assertEquals(today.minusDays(30), startCap.getValue());
        }

        @Test
        @DisplayName("startDate 晚于 endDate 时抛出 BusinessException")
        void startAfterEnd_throws() {
            mockAuthUser(1L, AuthUtil.ROLE_ADMIN, null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reportService.getGlobalOrderReport(
                            LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1)));

            assertEquals(Result.CODE_BAD_REQUEST, ex.getCode());
            verify(orderMapper, never()).selectStatusBreakdown(any(), any(), any());
        }

        @Test
        @DisplayName("非 Admin 调用时抛出 BusinessException（code 403）")
        void nonAdmin_throws403() {
            mockAuthUser(10L, AuthUtil.ROLE_STAFF, 100L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> reportService.getGlobalOrderReport(null, null));

            assertEquals(Result.CODE_FORBIDDEN, ex.getCode());
            verify(orderMapper, never()).selectStatusBreakdown(any(), any(), any());
        }
    }

    private static StoreBreakdownResponse findStore(List<StoreBreakdownResponse> list, Long storeId) {
        return list.stream().filter(s -> storeId.equals(s.getStoreId())).findFirst()
                .orElseThrow(() -> new AssertionError("未找到商店 " + storeId));
    }
}
