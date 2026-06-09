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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        AuthUtil.requireRole(customer, AuthUtil.ROLE_CUSTOMER);

        ChatSession session = resolveOrCreateSession(request.getStoreId(), customer.userId());

        ChatMessage saved = persistMessage(session, AuthUtil.ROLE_CUSTOMER, customer.userId(), request.getContent());
        ChatMessageResponse view = toMessageView(saved);

        if (session.getAssigneeStaffId() == null) {
            Long storeId = session.getStoreId();
            Long sid = session.getId();
            afterCommit(() -> messagingTemplate.convertAndSend("/topic/store." + storeId,
                    StoreTopicEvent.builder()
                            .type(StoreTopicEvent.TYPE_MESSAGE)
                            .sessionId(sid)
                            .message(view)
                            .build()));
        } else {
            Long assignee = session.getAssigneeStaffId();
            afterCommit(() -> messagingTemplate.convertAndSendToUser(
                    String.valueOf(assignee), "/queue/messages", view));
        }
        return view;
    }

    /**
     * 在当前事务提交后执行（无活动事务时立即执行）。
     * 用于把 STOMP 推送延迟到 DB 写入提交之后，避免客服收到事件后立刻用 sessionId 调 REST
     * 却因会话行尚未提交而读不到（REST 为独立事务，MySQL 默认 REPEATABLE READ）。
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
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

    // ── 客服回复 ───────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public ChatMessageResponse sendFromStaff(AuthUser staff, StaffSendRequest request) {
        AuthUtil.requireRole(staff, AuthUtil.ROLE_STAFF);

        ChatSession session = requireSession(request.getSessionId(), staff);
        if (!staff.userId().equals(session.getAssigneeStaffId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "请先接入会话");
        }

        ChatMessage saved = persistMessage(session, AuthUtil.ROLE_STAFF, staff.userId(), request.getContent());
        ChatMessageResponse view = toMessageView(saved);

        Long customerId = session.getCustomerId();
        afterCommit(() -> messagingTemplate.convertAndSendToUser(
                String.valueOf(customerId), "/queue/messages", view));
        return view;
    }

    // ── 认领会话 ───────────────────────────────────────────────────────────────
    @Override
    public ClaimResponse claim(AuthUser staff, Long sessionId) {
        AuthUtil.requireRole(staff, AuthUtil.ROLE_STAFF);

        ChatSession session = requireSession(sessionId, staff);

        int affected = baseMapper.claim(sessionId, staff.userId());
        if (affected == 0) {
            // 已被抢先（或刚好被释放）：尽力取出当前接待人姓名做友好提示
            ChatSession latest = getById(sessionId);
            String name = nicknameOf(latest != null ? latest.getAssigneeStaffId() : null);
            throw new BusinessException("会话已被 " + (name.isEmpty() ? "其他客服" : name) + " 接待");
        }

        String staffName = nicknameOf(staff.userId());
        Long storeId = session.getStoreId();
        afterCommit(() -> messagingTemplate.convertAndSend("/topic/store." + storeId,
                StoreTopicEvent.builder()
                        .type(StoreTopicEvent.TYPE_CLAIMED)
                        .sessionId(sessionId)
                        .assigneeStaffId(staff.userId())
                        .assigneeName(staffName)
                        .build()));

        return ClaimResponse.builder().sessionId(sessionId).assigneeStaffId(staff.userId()).build();
    }

    // ── 释放会话 ───────────────────────────────────────────────────────────────
    @Override
    public void release(AuthUser staff, Long sessionId) {
        AuthUtil.requireRole(staff, AuthUtil.ROLE_STAFF);

        ChatSession session = requireSession(sessionId, staff);

        int affected = baseMapper.release(sessionId, staff.userId());
        if (affected == 0) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "仅接待人可释放会话");
        }

        Long storeId = session.getStoreId();
        afterCommit(() -> messagingTemplate.convertAndSend("/topic/store." + storeId,
                StoreTopicEvent.builder()
                        .type(StoreTopicEvent.TYPE_RELEASED)
                        .sessionId(sessionId)
                        .build()));
    }

    @Override
    public List<ChatSessionItemResponse> listSessions(AuthUser user) {
        boolean isStaff = AuthUtil.ROLE_STAFF.equals(user.role());
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .orderByDesc(ChatSession::getLastMessageAt);
        if (isStaff) {
            wrapper.eq(ChatSession::getStoreId, user.storeId());
        } else {
            wrapper.eq(ChatSession::getCustomerId, user.userId());
        }
        List<ChatSession> sessions = list(wrapper);

        return sessions.stream().map(s -> {
            ChatSessionItemResponse.ChatSessionItemResponseBuilder b = ChatSessionItemResponse.builder()
                    .sessionId(s.getId())
                    .storeId(s.getStoreId())
                    .customerId(s.getCustomerId())
                    .assigneeStaffId(s.getAssigneeStaffId())
                    .assigneeName(s.getAssigneeStaffId() == null ? null : nicknameOf(s.getAssigneeStaffId()))
                    .lastMessage(s.getLastMessage())
                    .lastMessageAt(s.getLastMessageAt());
            if (isStaff) {
                b.peerOnline(Boolean.TRUE.equals(redisUtil.sIsMember(ChatKeys.ONLINE_CUSTOMERS, String.valueOf(s.getCustomerId()))));
            } else {
                Store store = storeMapper.selectById(s.getStoreId());
                b.storeName(store != null ? store.getName() : null);
                b.peerOnline(redisUtil.sCard(ChatKeys.onlineStoreStaff(s.getStoreId())) > 0);
            }
            return b.build();
        }).toList();
    }

    @Override
    public List<ChatMessageResponse> getMessages(AuthUser user, Long sessionId, Long before, int size) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "会话不存在");
        }
        if (AuthUtil.ROLE_STAFF.equals(user.role())) {
            if (!session.getStoreId().equals(user.storeId())) {
                throw new BusinessException(Result.CODE_FORBIDDEN, "无权查看该会话");
            }
        } else {
            if (!session.getCustomerId().equals(user.userId())) {
                throw new BusinessException(Result.CODE_FORBIDDEN, "无权查看该会话");
            }
        }

        int pageSize = (size <= 0 || size > 100) ? 20 : size;
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + pageSize);
        if (before != null) {
            wrapper.lt(ChatMessage::getId, before);
        }
        return chatMessageMapper.selectList(wrapper).stream().map(this::toMessageView).toList();
    }

    /** 加载会话并校验存在 + 当前 Staff 属于该会话所属店铺（客服按店操作的统一前置校验）。 */
    private ChatSession requireSession(Long sessionId, AuthUser staff) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "会话不存在");
        }
        if (!session.getStoreId().equals(staff.storeId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权操作该会话");
        }
        return session;
    }

    /** 取用户昵称，缺失时回退为"客服{id}"。 */
    private String nicknameOf(Long userId) {
        if (userId == null) return "";
        User u = userMapper.selectById(userId);
        return (u != null && u.getNickname() != null) ? u.getNickname() : ("客服" + userId);
    }
}
