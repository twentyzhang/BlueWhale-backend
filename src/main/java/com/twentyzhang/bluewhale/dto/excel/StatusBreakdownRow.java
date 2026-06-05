package com.twentyzhang.bluewhale.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 门店报表「状态分布」Sheet 的一行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusBreakdownRow {

    @ExcelProperty("订单状态")
    private String status;

    @ExcelProperty("数量")
    private Integer count;
}
