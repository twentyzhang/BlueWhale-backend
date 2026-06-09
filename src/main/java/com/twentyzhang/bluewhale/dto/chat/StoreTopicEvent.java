package com.twentyzhang.bluewhale.dto.chat;

import lombok.Builder;
import lombok.Data;

/**
 * 店铺主题 /topic/store.{storeId} 上的统一信封。
 * type=MESSAGE 时 message 有值；CLAIMED/RELEASED 时 sessionId/assignee* 有值。
 */
@Data
@Builder
public class StoreTopicEvent {

    public static final String TYPE_MESSAGE  = "MESSAGE";
    public static final String TYPE_CLAIMED  = "CLAIMED";
    public static final String TYPE_RELEASED = "RELEASED";

    private String type;
    private Long sessionId;
    private ChatMessageResponse message;   // type=MESSAGE 时填充
    private Long assigneeStaffId;          // type=CLAIMED 时填充
    private String assigneeName;           // type=CLAIMED 时填充
}
