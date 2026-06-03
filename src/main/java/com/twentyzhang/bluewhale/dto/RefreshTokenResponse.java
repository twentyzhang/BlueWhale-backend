package com.twentyzhang.bluewhale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenResponse {

    /** 新签发的 accessToken */
    private String token;

    /** 轮换后的新 refreshToken（前端需替换本地存储的旧值） */
    private String refreshToken;
}
