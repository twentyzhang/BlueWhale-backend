package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateStoreRequest {

    @NotBlank(message = "商店名称不能为空")
    private String name;

    @NotBlank(message = "统一社会信用代码不能为空")
    private String creditCode;

    private String logo;

    @NotBlank(message = "初始员工手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String staffPhone;
}
