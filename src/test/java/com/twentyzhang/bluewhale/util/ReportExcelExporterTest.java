package com.twentyzhang.bluewhale.util;

import com.twentyzhang.bluewhale.dto.DailyRevenueResponse;
import com.twentyzhang.bluewhale.dto.GlobalOrderReportResponse;
import com.twentyzhang.bluewhale.dto.StoreBreakdownResponse;
import com.twentyzhang.bluewhale.dto.StoreOrderReportResponse;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportExcelExporter")
class ReportExcelExporterTest {

    @Test
    @DisplayName("门店报表导出为三个 Sheet：汇总/状态分布/每日收入，行数正确")
    void writeStoreReport_producesThreeSheets() throws Exception {
        Map<String, Integer> status = new LinkedHashMap<>();
        status.put("COMPLETED", 100);
        status.put("CANCELLED", 15);
        StoreOrderReportResponse data = StoreOrderReportResponse.builder()
                .totalOrders(115)
                .totalRevenue(new BigDecimal("3840.50"))
                .averageOrderAmount(new BigDecimal("33.39"))
                .statusBreakdown(status)
                .dailyRevenue(List.of(
                        DailyRevenueResponse.builder().date("2026-05-19").revenue(new BigDecimal("560.00")).orders(18).build(),
                        DailyRevenueResponse.builder().date("2026-05-20").revenue(new BigDecimal("480.00")).orders(15).build()))
                .build();

        try (Workbook wb = writeAndOpen(out -> ReportExcelExporter.writeStoreReport(out, data))) {
            assertEquals(3, wb.getNumberOfSheets());
            assertEquals("汇总", wb.getSheetName(0));
            assertEquals("状态分布", wb.getSheetName(1));
            assertEquals("每日收入", wb.getSheetName(2));
            // 汇总：表头 + 3 行指标
            assertEquals(3, wb.getSheetAt(0).getLastRowNum());
            // 状态分布：表头 + 2 行
            assertEquals(2, wb.getSheetAt(1).getLastRowNum());
            // 每日收入：表头 + 2 行
            assertEquals(2, wb.getSheetAt(2).getLastRowNum());
            // 抽查一个值：状态分布首行状态名
            assertEquals("COMPLETED", wb.getSheetAt(1).getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    @DisplayName("全局报表导出为三个 Sheet：汇总/门店分布/每日收入")
    void writeGlobalReport_producesThreeSheets() throws Exception {
        GlobalOrderReportResponse data = GlobalOrderReportResponse.builder()
                .totalOrders(860)
                .totalRevenue(new BigDecimal("28600.00"))
                .storeBreakdown(List.of(
                        StoreBreakdownResponse.builder().storeId(1L).storeName("南鲸旗舰店")
                                .orders(128).revenue(new BigDecimal("3840.50")).build()))
                .dailyRevenue(List.of(
                        DailyRevenueResponse.builder().date("2026-05-20").revenue(new BigDecimal("1200.00")).orders(40).build()))
                .build();

        try (Workbook wb = writeAndOpen(out -> ReportExcelExporter.writeGlobalReport(out, data))) {
            assertEquals(3, wb.getNumberOfSheets());
            assertEquals("汇总", wb.getSheetName(0));
            assertEquals("门店分布", wb.getSheetName(1));
            assertEquals("每日收入", wb.getSheetName(2));
            assertEquals("南鲸旗舰店", wb.getSheetAt(1).getRow(1).getCell(1).getStringCellValue());
        }
    }

    @Test
    @DisplayName("空集合/空字段不报错，仍生成三个 Sheet")
    void writeStoreReport_handlesNullsGracefully() throws Exception {
        StoreOrderReportResponse data = StoreOrderReportResponse.builder()
                .totalOrders(0)
                .totalRevenue(BigDecimal.ZERO)
                .averageOrderAmount(BigDecimal.ZERO)
                .statusBreakdown(null)
                .dailyRevenue(null)
                .build();

        try (Workbook wb = writeAndOpen(out -> ReportExcelExporter.writeStoreReport(out, data))) {
            assertEquals(3, wb.getNumberOfSheets());
            // 仅表头，无数据行
            assertEquals(0, wb.getSheetAt(1).getLastRowNum());
            assertEquals(0, wb.getSheetAt(2).getLastRowNum());
        }
    }

    private interface Exporter {
        void write(ByteArrayOutputStream out);
    }

    private Workbook writeAndOpen(Exporter exporter) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out);
        byte[] bytes = out.toByteArray();
        // xlsx 本质是 zip，首字节应为 PK
        assertTrue(bytes.length > 0 && bytes[0] == 'P' && bytes[1] == 'K');
        return WorkbookFactory.create(new ByteArrayInputStream(bytes));
    }
}
