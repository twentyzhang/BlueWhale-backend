package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShipOrderRequest {

    @NotBlank(message = "快递单号不能为空")
    private String trackingNumber;

    @NotBlank(message = "快递公司不能为空")
    private String carrier;
}
