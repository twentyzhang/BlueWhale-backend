package com.twentyzhang.bluewhale.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.twentyzhang.bluewhale.dto.DailyRevenueResponse;
import com.twentyzhang.bluewhale.dto.GlobalOrderReportResponse;
import com.twentyzhang.bluewhale.dto.StoreBreakdownResponse;
import com.twentyzhang.bluewhale.dto.StoreOrderReportResponse;
import com.twentyzhang.bluewhale.dto.excel.DailyRevenueRow;
import com.twentyzhang.bluewhale.dto.excel.ReportSummaryRow;
import com.twentyzhang.bluewhale.dto.excel.StatusBreakdownRow;
import com.twentyzhang.bluewhale.dto.excel.StoreBreakdownRow;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把报表 DTO 写成多 Sheet 的 xlsx 工作簿。
 * 只负责「数据 → Excel 字节流」，鉴权与 HTTP 响应头由调用方处理。
 */
public final class ReportExcelExporter {

    private ReportExcelExporter() {}

    /**
     * 门店订单报表 → 三个 Sheet：汇总 / 状态分布 / 每日收入。
     */
    public static void writeStoreReport(OutputStream out, StoreOrderReportResponse data) {
        List<ReportSummaryRow> summary = List.of(
                new ReportSummaryRow("总订单数", String.valueOf(data.getTotalOrders())),
                new ReportSummaryRow("总收入", String.valueOf(data.getTotalRevenue())),
                new ReportSummaryRow("均单价", String.valueOf(data.getAverageOrderAmount())));

        List<StatusBreakdownRow> statusRows = new ArrayList<>();
        if (data.getStatusBreakdown() != null) {
            for (Map.Entry<String, Integer> e : data.getStatusBreakdown().entrySet()) {
                statusRows.add(new StatusBreakdownRow(e.getKey(), e.getValue()));
            }
        }

        try (ExcelWriter writer = EasyExcel.write(out).build()) {
            WriteSheet summarySheet = EasyExcel.writerSheet(0, "汇总").head(ReportSummaryRow.class).build();
            writer.write(summary, summarySheet);

            WriteSheet statusSheet = EasyExcel.writerSheet(1, "状态分布").head(StatusBreakdownRow.class).build();
            writer.write(statusRows, statusSheet);

            WriteSheet dailySheet = EasyExcel.writerSheet(2, "每日收入").head(DailyRevenueRow.class).build();
            writer.write(toDailyRows(data.getDailyRevenue()), dailySheet);
        }
    }

    /**
     * 全局汇总报表 → 三个 Sheet：汇总 / 门店分布 / 每日收入。
     */
    public static void writeGlobalReport(OutputStream out, GlobalOrderReportResponse data) {
        List<ReportSummaryRow> summary = List.of(
                new ReportSummaryRow("总订单数", String.valueOf(data.getTotalOrders())),
                new ReportSummaryRow("总收入", String.valueOf(data.getTotalRevenue())));

        List<StoreBreakdownRow> storeRows = new ArrayList<>();
        if (data.getStoreBreakdown() != null) {
            for (StoreBreakdownResponse s : data.getStoreBreakdown()) {
                storeRows.add(new StoreBreakdownRow(
                        s.getStoreId(), s.getStoreName(), s.getOrders(), s.getRevenue()));
            }
        }

        try (ExcelWriter writer = EasyExcel.write(out).build()) {
            WriteSheet summarySheet = EasyExcel.writerSheet(0, "汇总").head(ReportSummaryRow.class).build();
            writer.write(summary, summarySheet);

            WriteSheet storeSheet = EasyExcel.writerSheet(1, "门店分布").head(StoreBreakdownRow.class).build();
            writer.write(storeRows, storeSheet);

            WriteSheet dailySheet = EasyExcel.writerSheet(2, "每日收入").head(DailyRevenueRow.class).build();
            writer.write(toDailyRows(data.getDailyRevenue()), dailySheet);
        }
    }

    private static List<DailyRevenueRow> toDailyRows(List<DailyRevenueResponse> daily) {
        List<DailyRevenueRow> rows = new ArrayList<>();
        if (daily != null) {
            for (DailyRevenueResponse d : daily) {
                rows.add(new DailyRevenueRow(d.getDate(), d.getOrders(), d.getRevenue()));
            }
        }
        return rows;
    }
}
