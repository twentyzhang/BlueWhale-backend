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
public class MyCouponResponse {

    private Long id;
    private String groupName;
    /** DISCOUNT / FULL_REDUCTION / DIRECT_OFF */
    private String type;
    private BigDecimal value;
    private BigDecimal minOrderAmount;
    private LocalDateTime expireAt;
    /** UNUSED / USED / EXPIRED */
    private String status;
    private Long storeId;
}
