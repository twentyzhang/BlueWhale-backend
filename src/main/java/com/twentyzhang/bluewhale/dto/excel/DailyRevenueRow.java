package com.twentyzhang.bluewhale.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 报表「每日收入」Sheet 的一行（门店 / 全局共用，仅统计 COMPLETED 订单）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueRow {

    @ExcelProperty("日期")
    private String date;

    @ExcelProperty("订单数")
    private Integer orders;

    @ExcelProperty("收入")
    private BigDecimal revenue;
}
