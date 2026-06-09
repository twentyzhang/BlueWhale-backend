package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.BaseServiceTest;
import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.dto.chat.ChatMessageResponse;
import com.twentyzhang.bluewhale.dto.chat.CustomerSendRequest;
import com.twentyzhang.bluewhale.dto.chat.StoreTopicEvent;
import com.twentyzhang.bluewhale.entity.ChatSession;
import com.twentyzhang.bluewhale.mapper.ChatMessageMapper;
import com.twentyzhang.bluewhale.mapper.ChatSessionMapper;
import com.twentyzhang.bluewhale.mapper.StoreMapper;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import com.twentyzhang.bluewhale.service.impl.ChatServiceImpl;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ChatService")
class ChatServiceTest extends BaseServiceTest {

    @Mock private ChatSessionMapper chatSessionMapper;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private UserMapper userMapper;
    @Mock private RedisUtil redisUtil;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatServiceImpl chatService;

    private static ChatSession anySession() { return ArgumentMatchers.any(ChatSession.class); }
    private static com.twentyzhang.bluewhale.entity.ChatMessage anyMsg() {
        return ArgumentMatchers.any(com.twentyzhang.bluewhale.entity.ChatMessage.class);
    }

    private static final AuthUser CUSTOMER = new AuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
    private static final AuthUser STAFF    = new AuthUser(9L, AuthUtil.ROLE_STAFF, 5L);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(chatService, "baseMapper", chatSessionMapper);
    }

    private static CustomerSendRequest custReq(Long storeId, String content) {
        CustomerSendRequest r = new CustomerSendRequest();
        r.setStoreId(storeId);
        r.setContent(content);
        return r;
    }

    private static ChatSession session(Long id, Long storeId, Long customerId, Long assignee) {
        return ChatSession.builder().id(id).storeId(storeId).customerId(customerId).assigneeStaffId(assignee).build();
    }

    @Test
    @DisplayName("买家发消息：会话不存在时自动创建并落库")
    void customerSend_autoCreatesSession() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        when(chatSessionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(chatSessionMapper.insert(anySession())).thenAnswer(inv -> {
            ((ChatSession) inv.getArgument(0)).setId(100L);
            return 1;
        });
        when(chatMessageMapper.insert(anyMsg())).thenAnswer(inv -> {
            ((com.twentyzhang.bluewhale.entity.ChatMessage) inv.getArgument(0)).setId(7L);
            return 1;
        });
        when(chatSessionMapper.updateById(anySession())).thenReturn(1);

        ChatMessageResponse view = chatService.sendFromCustomer(CUSTOMER, custReq(5L, "你好"));

        assertEquals(7L, view.getId());
        assertEquals("CUSTOMER", view.getSenderRole());
        verify(chatSessionMapper).insert(anySession());
    }

    @Test
    @DisplayName("买家发消息：未接入会话广播到店铺主题")
    void customerSend_unassigned_broadcastsToStoreTopic() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        when(chatSessionMapper.selectOne(any(), anyBoolean())).thenReturn(session(100L, 5L, 1L, null));
        when(chatMessageMapper.insert(anyMsg())).thenReturn(1);
        when(chatSessionMapper.updateById(anySession())).thenReturn(1);

        chatService.sendFromCustomer(CUSTOMER, custReq(5L, "在吗"));

        verify(messagingTemplate).convertAndSend(eq("/topic/store.5"), any(StoreTopicEvent.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("买家发消息：已接入会话精准投递归属客服")
    void customerSend_assigned_sendsToAssignee() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        when(chatSessionMapper.selectOne(any(), anyBoolean())).thenReturn(session(100L, 5L, 1L, 9L));
        when(chatMessageMapper.insert(anyMsg())).thenReturn(1);
        when(chatSessionMapper.updateById(anySession())).thenReturn(1);

        chatService.sendFromCustomer(CUSTOMER, custReq(5L, "问个问题"));

        verify(messagingTemplate).convertAndSendToUser(eq("9"), eq("/queue/messages"), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("非 Customer 调用买家发消息抛 403")
    void customerSend_notCustomer_throws403() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        assertThrows(com.twentyzhang.bluewhale.exception.BusinessException.class,
                () -> chatService.sendFromCustomer(STAFF, custReq(5L, "x")));
    }
}
