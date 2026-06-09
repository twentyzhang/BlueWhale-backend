package com.twentyzhang.bluewhale.dto.chat;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 单条消息视图（投递给买家/客服、历史拉取均用） */
@Data
@Builder
public class ChatMessageResponse {
    private Long id;
    private Long sessionId;
    private String senderRole;   // CUSTOMER / STAFF
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;
}
