package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateStockRequest {

    @NotNull(message = "变更数量不能为空")
    @Min(value = 1, message = "变更数量必须为正整数")
    private Integer delta;

    @NotNull(message = "变更类型不能为空")
    @Pattern(regexp = "^(IN|OUT)$", message = "变更类型须为 IN 或 OUT")
    private String type;
}
