package com.twentyzhang.bluewhale.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 客服回复消息：SEND /app/chat.staff.send */
@Data
public class StaffSendRequest {

    @NotNull(message = "sessionId 不能为空")
    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 1000, message = "消息长度不能超过 1000 字")
    private String content;
}
