package com.twentyzhang.bluewhale.dto.chat;

import lombok.Builder;
import lombok.Data;

/** 接入成功响应（失败走 BusinessException） */
@Data
@Builder
public class ClaimResponse {
    private Long sessionId;
    private Long assigneeStaffId;
}
