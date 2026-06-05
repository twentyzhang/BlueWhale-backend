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
public class CouponGroupResponse {

    private Long id;
    private String name;
    /** DISCOUNT / FULL_REDUCTION / DIRECT_OFF */
    private String type;
    private BigDecimal value;
    private BigDecimal minOrderAmount;
    private LocalDateTime expireAt;
    private Integer totalCount;
    private Integer remainCount;
    private Long storeId;
    private String storeName;
}
