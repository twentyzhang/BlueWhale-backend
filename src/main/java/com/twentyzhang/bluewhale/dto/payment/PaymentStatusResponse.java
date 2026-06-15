package com.twentyzhang.bluewhale.dto.payment;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {
    private String tradeNo;
    private String status;      // PENDING / SUCCESS / FAILED
    private Long orderId;
    private BigDecimal amount;
    private LocalDateTime paidAt;
}
