package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.dto.DailyRevenueResponse;
import com.twentyzhang.bluewhale.dto.GlobalOrderReportResponse;
import com.twentyzhang.bluewhale.dto.StoreBreakdownResponse;
import com.twentyzhang.bluewhale.dto.StoreOrderReportResponse;
import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.service.ReportService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.CacheKeys;
import com.twentyzhang.bluewhale.util.CacheUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl extends ServiceImpl<OrderMapper, Order> implements ReportService {

    private final OrderMapper orderMapper;
    private final CacheUtil cacheUtil;

    // ── 4. getStoreOrderReport ────────────────────────────────────────────────

    @Override
    public StoreOrderReportResponse getStoreOrderReport(Long storeId, LocalDate startDate, LocalDate endDate) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);
        AuthUtil.requireStoreAccess(storeId);

        LocalDate[] range = normalizeAndValidate(startDate, endDate);
        return cacheUtil.getOrLoad(CacheKeys.storeReport(storeId, range[0], range[1]),
                CacheKeys.REPORT_TTL, CacheKeys.REPORT_JITTER,
                () -> loadStoreOrderReport(storeId, range[0], range[1]),
                StoreOrderReportResponse.class);
    }

    private StoreOrderReportResponse loadStoreOrderReport(Long storeId, LocalDate effectiveStart, LocalDate effectiveEnd) {
        // 各状态订单数（totalOrders 含所有状态）
        Map<String, Integer> statusBreakdown = new LinkedHashMap<>();
        long totalOrders = 0;
        for (Map<String, Object> row :
                orderMapper.selectStatusBreakdown(storeId, effectiveStart, effectiveEnd)) {
            String status = (String) row.get("status");
            int cnt = ((Number) row.get("cnt")).intValue();
            statusBreakdown.put(status, cnt);
            totalOrders += cnt;
        }

        // 按日收入（仅 COMPLETED 订单）
        List<DailyRevenueResponse> dailyRevenue = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Map<String, Object> row :
                orderMapper.selectDailyRevenue(storeId, effectiveStart, effectiveEnd)) {
            BigDecimal revenue = toBigDecimal(row.get("revenue"));
            dailyRevenue.add(DailyRevenueResponse.builder()
                    .date(row.get("date").toString())
                    .revenue(revenue)
                    .orders(((Number) row.get("orders")).intValue())
                    .build());
            totalRevenue = totalRevenue.add(revenue);
        }

        BigDecimal avgOrderAmount = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return StoreOrderReportResponse.builder()
                .totalOrders((int) totalOrders)
                .totalRevenue(totalRevenue)
                .averageOrderAmount(avgOrderAmount)
                .statusBreakdown(statusBreakdown)
                .dailyRevenue(dailyRevenue)
                .build();
    }

    // ── 5. getGlobalOrderReport ───────────────────────────────────────────────

    @Override
    public GlobalOrderReportResponse getGlobalOrderReport(LocalDate startDate, LocalDate endDate) {
        AuthUtil.requireRole(AuthUtil.ROLE_ADMIN);

        LocalDate[] range = normalizeAndValidate(startDate, endDate);
        return cacheUtil.getOrLoad(CacheKeys.globalReport(range[0], range[1]),
                CacheKeys.REPORT_TTL, CacheKeys.REPORT_JITTER,
                () -> loadGlobalOrderReport(range[0], range[1]),
                GlobalOrderReportResponse.class);
    }

    private GlobalOrderReportResponse loadGlobalOrderReport(LocalDate effectiveStart, LocalDate effectiveEnd) {
        // 全局各状态订单数 → 计算 totalOrders
        long totalOrders = 0;
        for (Map<String, Object> row :
                orderMapper.selectStatusBreakdown(null, effectiveStart, effectiveEnd)) {
            totalOrders += ((Number) row.get("cnt")).longValue();
        }

        // 按日收入（全局，仅 COMPLETED）
        List<DailyRevenueResponse> dailyRevenue = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Map<String, Object> row :
                orderMapper.selectDailyRevenue(null, effectiveStart, effectiveEnd)) {
            BigDecimal revenue = toBigDecimal(row.get("revenue"));
            dailyRevenue.add(DailyRevenueResponse.builder()
                    .date(row.get("date").toString())
                    .revenue(revenue)
                    .orders(((Number) row.get("orders")).intValue())
                    .build());
            totalRevenue = totalRevenue.add(revenue);
        }

        // 各商店收入分布
        List<StoreBreakdownResponse> storeBreakdown = new ArrayList<>();
        for (Map<String, Object> row :
                orderMapper.selectStoreBreakdown(effectiveStart, effectiveEnd)) {
            storeBreakdown.add(StoreBreakdownResponse.builder()
                    .storeId(((Number) row.get("storeId")).longValue())
                    .storeName((String) row.get("storeName"))
                    .orders(((Number) row.get("orders")).intValue())
                    .revenue(toBigDecimal(row.get("revenue")))
                    .build());
        }

        return GlobalOrderReportResponse.builder()
                .totalOrders((int) totalOrders)
                .totalRevenue(totalRevenue)
                .storeBreakdown(storeBreakdown)
                .dailyRevenue(dailyRevenue)
                .build();
    }

    // ── 私有辅助方法 ──────────────────────────────────────────────────────────

    /**
     * 不传 startDate/endDate 时默认最近 30 天；验证 start <= end。
     */
    private LocalDate[] normalizeAndValidate(LocalDate startDate, LocalDate endDate) {
        LocalDate effectiveEnd   = endDate   != null ? endDate   : LocalDate.now();
        LocalDate effectiveStart = startDate != null ? startDate : effectiveEnd.minusDays(30);

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new BusinessException("起始日期不能晚于截止日期");
        }
        return new LocalDate[]{effectiveStart, effectiveEnd};
    }

    /** 安全地将 Object 转换为 BigDecimal，null 返回 ZERO。 */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null)              return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return new BigDecimal(value.toString());
    }
}
