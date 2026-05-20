package com.twentyzhang.bluewhale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalOrderReportResponse {

    private Integer totalOrders;
    private BigDecimal totalRevenue;
    private List<StoreBreakdownResponse> storeBreakdown;
    private List<DailyRevenueResponse> dailyRevenue;
}
