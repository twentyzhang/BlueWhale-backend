package com.twentyzhang.bluewhale.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 买家发送消息：SEND /app/chat.customer.send */
@Data
public class CustomerSendRequest {

    @NotNull(message = "storeId 不能为空")
    private Long storeId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 1000, message = "消息长度不能超过 1000 字")
    private String content;
}
