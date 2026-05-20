package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProductRequest {

    private String name;

    @DecimalMin(value = "0.01", message = "商品价格必须大于 0")
    private BigDecimal price;

    private Long categoryId;
    private String imageUrl;
}
