package com.twentyzhang.bluewhale.dto.chat;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话列表项（买家/客服共用，字段按角色填充） */
@Data
@Builder
public class ChatSessionItemResponse {
    private Long sessionId;
    private Long storeId;
    private String storeName;        // 买家视角填店名
    private Long customerId;
    private Long assigneeStaffId;     // null=未接入
    private String assigneeName;      // 接待客服昵称
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private boolean peerOnline;       // 买家视角=本店有客服在线；客服视角=该买家在线
}
