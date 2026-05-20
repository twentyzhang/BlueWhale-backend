package com.twentyzhang.bluewhale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通用新建资源响应，data 为 { "id": N }。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdResponse {
    private Long id;
}
