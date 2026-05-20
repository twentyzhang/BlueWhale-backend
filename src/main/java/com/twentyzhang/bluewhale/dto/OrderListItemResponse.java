package com.twentyzhang.bluewhale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListItemResponse {

    private Long id;
    private String status;
    private BigDecimal payableAmount;
    private LocalDateTime createdAt;
    private String storeName;
    private Integer itemCount;
}
