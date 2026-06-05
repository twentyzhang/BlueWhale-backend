package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateCouponGroupRequest {

    @NotBlank(message = "优惠券名称不能为空")
    private String name;

    @NotNull(message = "优惠券类型不能为空")
    @Pattern(regexp = "^(DISCOUNT|FULL_REDUCTION|DIRECT_OFF)$",
            message = "优惠券类型须为 DISCOUNT（折扣）/ FULL_REDUCTION（满减）/ DIRECT_OFF（直减）")
    private String type;

    @NotNull(message = "优惠值不能为空")
    @DecimalMin(value = "0.01", message = "优惠值必须大于 0")
    private BigDecimal value;

    @NotNull(message = "最低订单金额不能为空")
    @DecimalMin(value = "0.00", message = "最低订单金额不能为负数")
    private BigDecimal minOrderAmount;

    @NotNull(message = "总数量不能为空")
    @Min(value = 1, message = "总数量至少为 1")
    private Integer totalCount;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startAt;

    @NotNull(message = "过期时间不能为空")
    private LocalDateTime expireAt;
}
