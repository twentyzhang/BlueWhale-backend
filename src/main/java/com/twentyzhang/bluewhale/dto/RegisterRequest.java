package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度须在 6-20 位之间")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称长度须在 2-20 个字符之间")
    private String nickname;

    /** 注册角色：CUSTOMER / STAFF */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(CUSTOMER|STAFF)$", message = "角色只能为 CUSTOMER 或 STAFF")
    private String role;
}
