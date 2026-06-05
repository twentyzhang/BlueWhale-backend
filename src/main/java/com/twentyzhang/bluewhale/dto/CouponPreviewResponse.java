package com.twentyzhang.bluewhale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券试算结果：在最优叠加顺序下的折扣与实付。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponPreviewResponse {

    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
    /** 取得最优优惠时券的应用顺序（券 ID） */
    private List<Long> appliedCouponIds;
}
