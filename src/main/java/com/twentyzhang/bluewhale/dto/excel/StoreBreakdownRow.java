package com.twentyzhang.bluewhale.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 全局报表「门店分布」Sheet 的一行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreBreakdownRow {

    @ExcelProperty("门店ID")
    private Long storeId;

    @ExcelProperty("门店名称")
    private String storeName;

    @ExcelProperty("订单数")
    private Integer orders;

    @ExcelProperty("收入")
    private BigDecimal revenue;
}
