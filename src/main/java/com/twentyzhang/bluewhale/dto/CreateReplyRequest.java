package com.twentyzhang.bluewhale.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateReplyRequest {

    @NotBlank(message = "回复内容不能为空")
    private String content;
}
