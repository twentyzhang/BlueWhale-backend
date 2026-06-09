package com.twentyzhang.bluewhale.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.chat.ChatMessageResponse;
import com.twentyzhang.bluewhale.dto.chat.ChatSessionItemResponse;
import com.twentyzhang.bluewhale.dto.chat.ClaimResponse;
import com.twentyzhang.bluewhale.dto.chat.CustomerSendRequest;
import com.twentyzhang.bluewhale.dto.chat.StaffSendRequest;
import com.twentyzhang.bluewhale.dto.chat.StoreTopicEvent;
import com.twentyzhang.bluewhale.entity.ChatMessage;
import com.twentyzhang.bluewhale.entity.ChatSession;
import com.twentyzhang.bluewhale.entity.Store;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.exception.BusinessException;
import com.twentyzhang.bluewhale.mapper.ChatMessageMapper;
import com.twentyzhang.bluewhale.mapper.ChatSessionMapper;
import com.twentyzhang.bluewhale.mapper.StoreMapper;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.service.ChatService;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.ChatKeys;
import com.twentyzhang.bluewhale.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final StoreMapper storeMapper;
    private final UserMapper userMapper;
    private final RedisUtil redisUtil;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int LAST_MESSAGE_PREVIEW_MAX = 120;

    // ── 买家发消息 ─────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public ChatMessageResponse sendFromCustomer(AuthUser customer, CustomerSendRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        ChatSession session = resolveOrCreateSession(request.getStoreId(), customer.userId());

        ChatMessage saved = persistMessage(session, AuthUtil.ROLE_CUSTOMER, customer.userId(), request.getContent());
        ChatMessageResponse view = toMessageView(saved);

        if (session.getAssigneeStaffId() == null) {
            messagingTemplate.convertAndSend("/topic/store." + session.getStoreId(),
                    StoreTopicEvent.builder()
                            .type(StoreTopicEvent.TYPE_MESSAGE)
                            .sessionId(session.getId())
                            .message(view)
                            .build());
        } else {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(session.getAssigneeStaffId()), "/queue/messages", view);
        }
        return view;
    }

    // ── 私有辅助 ───────────────────────────────────────────────────────────────

    private ChatSession resolveOrCreateSession(Long storeId, Long customerId) {
        ChatSession existing = getOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getStoreId, storeId)
                .eq(ChatSession::getCustomerId, customerId));
        if (existing != null) {
            return existing;
        }
        ChatSession created = ChatSession.builder()
                .storeId(storeId)
                .customerId(customerId)
                .build();
        try {
            save(created);
            return created;
        } catch (DuplicateKeyException e) {
            return getOne(new LambdaQueryWrapper<ChatSession>()
                    .eq(ChatSession::getStoreId, storeId)
                    .eq(ChatSession::getCustomerId, customerId));
        }
    }

    private ChatMessage persistMessage(ChatSession session, String role, Long senderId, String content) {
        ChatMessage msg = ChatMessage.builder()
                .sessionId(session.getId())
                .senderRole(role)
                .senderId(senderId)
                .content(content)
                .build();
        chatMessageMapper.insert(msg);

        session.setLastMessage(previewOf(content));
        session.setLastMessageAt(LocalDateTime.now());
        updateById(session);
        return msg;
    }

    /** 截取会话列表预览，避免在 UTF-16 代理对（emoji）中间切断产生半个字符。 */
    private static String previewOf(String content) {
        if (content.length() <= LAST_MESSAGE_PREVIEW_MAX) {
            return content;
        }
        int end = LAST_MESSAGE_PREVIEW_MAX;
        if (Character.isHighSurrogate(content.charAt(end - 1))) {
            end--;
        }
        return content.substring(0, end);
    }

    private ChatMessageResponse toMessageView(ChatMessage m) {
        return ChatMessageResponse.builder()
                .id(m.getId())
                .sessionId(m.getSessionId())
                .senderRole(m.getSenderRole())
                .senderId(m.getSenderId())
                .content(m.getContent())
                .createdAt(m.getCreateTime())
                .build();
    }

    // ── 以下方法在后续任务实现 ───────────────────────────────────────────────
    @Override
    public ChatMessageResponse sendFromStaff(AuthUser staff, StaffSendRequest request) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public ClaimResponse claim(AuthUser staff, Long sessionId) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public void release(AuthUser staff, Long sessionId) {
        throw new UnsupportedOperationException("Task 6");
    }

    @Override
    public List<ChatSessionItemResponse> listSessions(AuthUser user) {
        throw new UnsupportedOperationException("Task 7");
    }

    @Override
    public List<ChatMessageResponse> getMessages(AuthUser user, Long sessionId, Long before, int size) {
        throw new UnsupportedOperationException("Task 7");
    }
}
