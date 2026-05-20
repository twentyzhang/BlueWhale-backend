package com.twentyzhang.bluewhale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    private Long id;
    /** 脱敏后的手机号，如 138****8000 */
    private String phone;
    private String nickname;
    /** CUSTOMER / STAFF / ADMIN */
    private String role;
    /** STAFF 所属商店 ID，其他角色为 null */
    private Long storeId;
}
