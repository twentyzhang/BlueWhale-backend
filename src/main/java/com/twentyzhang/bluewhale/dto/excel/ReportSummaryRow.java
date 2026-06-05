package com.twentyzhang.bluewhale.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报表「汇总」Sheet 的一行：指标名 + 数值（数值统一转字符串以兼容整数/金额）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportSummaryRow {

    @ExcelProperty("指标")
    private String metric;

    @ExcelProperty("数值")
    private String value;
}
