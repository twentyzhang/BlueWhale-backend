package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.dto.GlobalOrderReportResponse;
import com.twentyzhang.bluewhale.dto.StoreOrderReportResponse;
import com.twentyzhang.bluewhale.entity.Order;

import java.time.LocalDate;

public interface ReportService extends IService<Order> {

    /**
     * 获取指定商店的订单报表，包含总订单数、总收入、均单价、
     * 各状态订单分布（statusBreakdown）及按日收入明细（dailyRevenue）。
     * 仅 Staff 可调用。
     * 对应 GET /api/stores/{storeId}/reports/orders
     *
     * @param startDate 起始日期（含），null 表示不限
     * @param endDate   截止日期（含），null 表示不限
     */
    StoreOrderReportResponse getStoreOrderReport(Long storeId, LocalDate startDate, LocalDate endDate);

    /**
     * 获取全平台汇总报表，包含总订单数、总收入、
     * 各商店收入分布（storeBreakdown）及按日收入明细（dailyRevenue）。
     * 仅 Admin 可调用。
     * 对应 GET /api/admin/reports/orders
     *
     * @param startDate 起始日期（含），null 表示不限
     * @param endDate   截止日期（含），null 表示不限
     */
    GlobalOrderReportResponse getGlobalOrderReport(LocalDate startDate, LocalDate endDate);
}
