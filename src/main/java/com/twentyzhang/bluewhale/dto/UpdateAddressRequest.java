package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateAddressRequest {

    private String receiverName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private String province;
    private String city;
    private String district;
    private String detail;
    private Boolean isDefault;
}
