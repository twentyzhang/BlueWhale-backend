package com.twentyzhang.bluewhale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.GlobalOrderReportResponse;
import com.twentyzhang.bluewhale.dto.StoreListItemResponse;
import com.twentyzhang.bluewhale.service.ReportService;
import com.twentyzhang.bluewhale.service.StoreService;
import com.twentyzhang.bluewhale.util.ExcelDownloadUtil;
import com.twentyzhang.bluewhale.util.ReportExcelExporter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final StoreService storeService;
    private final ReportService reportService;

    // 需要鉴权 仅 Admin
    @GetMapping("/stores")
    public Result<IPage<StoreListItemResponse>> getAdminStoreList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(storeService.getAdminStoreList(page, size));
    }

    // 需要鉴权 仅 Admin
    @GetMapping("/reports/orders")
    public Result<GlobalOrderReportResponse> getGlobalOrderReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(reportService.getGlobalOrderReport(startDate, endDate));
    }

    // 需要鉴权 仅 Admin — 导出 xlsx
    @GetMapping("/reports/orders/export")
    public void exportGlobalOrderReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletResponse response) throws IOException {
        // 复用 JSON 接口的查询（自带 Admin 鉴权 + 缓存）
        GlobalOrderReportResponse data = reportService.getGlobalOrderReport(startDate, endDate);
        ExcelDownloadUtil.prepareResponse(response, "global-order-report.xlsx");
        ReportExcelExporter.writeGlobalReport(response.getOutputStream(), data);
    }
}
