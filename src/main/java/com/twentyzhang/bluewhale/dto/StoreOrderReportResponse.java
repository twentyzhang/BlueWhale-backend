package com.twentyzhang.bluewhale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreOrderReportResponse {

    private Integer totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal averageOrderAmount;
    /** key: 订单状态, value: 数量 */
    private Map<String, Integer> statusBreakdown;
    private List<DailyRevenueResponse> dailyRevenue;
}
