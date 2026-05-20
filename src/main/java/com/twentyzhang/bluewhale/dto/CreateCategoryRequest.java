package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "分类名称不能为空")
    @Size(min = 1, max = 20, message = "分类名称长度须在 1-20 个字符之间")
    private String name;

    /** null 表示创建顶级分类 */
    private Long parentId;
}
