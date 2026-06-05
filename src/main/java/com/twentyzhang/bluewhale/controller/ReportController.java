package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.StoreOrderReportResponse;
import com.twentyzhang.bluewhale.service.ReportService;
import com.twentyzhang.bluewhale.util.ExcelDownloadUtil;
import com.twentyzhang.bluewhale.util.ReportExcelExporter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // 需要鉴权 仅 Staff（本店）
    @GetMapping("/api/stores/{storeId}/reports/orders")
    public Result<StoreOrderReportResponse> getStoreOrderReport(
            @PathVariable Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(reportService.getStoreOrderReport(storeId, startDate, endDate));
    }

    // 需要鉴权 仅 Staff（本店）— 导出 xlsx
    @GetMapping("/api/stores/{storeId}/reports/orders/export")
    public void exportStoreOrderReport(
            @PathVariable Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletResponse response) throws IOException {
        // 复用 JSON 接口的查询（自带 Staff 鉴权 + 缓存）
        StoreOrderReportResponse data = reportService.getStoreOrderReport(storeId, startDate, endDate);
        ExcelDownloadUtil.prepareResponse(response, "store-" + storeId + "-order-report.xlsx");
        ReportExcelExporter.writeStoreReport(response.getOutputStream(), data);
    }
}
