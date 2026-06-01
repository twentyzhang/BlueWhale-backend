package com.twentyzhang.bluewhale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreListItemResponse {

    private Long id;
    private String name;
    private String logo;
    private Integer productCount;
    /** 仅 Admin 接口填充，公开接口为 null */
    private String creditCode;
}
