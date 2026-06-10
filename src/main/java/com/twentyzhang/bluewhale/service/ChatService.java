package com.twentyzhang.bluewhale.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.dto.chat.ChatMessageResponse;
import com.twentyzhang.bluewhale.dto.chat.ChatSessionItemResponse;
import com.twentyzhang.bluewhale.dto.chat.ClaimResponse;
import com.twentyzhang.bluewhale.dto.chat.CustomerSendRequest;
import com.twentyzhang.bluewhale.dto.chat.StaffSendRequest;
import com.twentyzhang.bluewhale.entity.ChatSession;

import java.util.List;

public interface ChatService extends IService<ChatSession> {

    ChatMessageResponse sendFromCustomer(AuthUser customer, CustomerSendRequest request);

    ChatMessageResponse sendFromStaff(AuthUser staff, StaffSendRequest request);

    ClaimResponse claim(AuthUser staff, Long sessionId);

    void release(AuthUser staff, Long sessionId);

    List<ChatSessionItemResponse> listSessions(AuthUser user);

    List<ChatMessageResponse> getMessages(AuthUser user, Long sessionId, Long before, int size);

    /**
     * 接待超时自动释放（系统级，定时任务调用，不读 SecurityContext）：
     * 扫描所有已接入会话，归属客服当前不在线者自动释放并广播 RELEASED。返回释放数量。
     */
    int autoReleaseOfflineAssignees();
}
