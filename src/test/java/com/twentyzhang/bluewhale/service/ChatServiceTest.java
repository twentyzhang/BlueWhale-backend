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
        com.twentyzhang.bluewhale.exception.BusinessException ex = assertThrows(
                com.twentyzhang.bluewhale.exception.BusinessException.class,
                () -> chatService.sendFromCustomer(STAFF, custReq(5L, "x")));
        assertEquals(com.twentyzhang.bluewhale.common.Result.CODE_FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("买家发消息：超长内容预览截断到 120，且不切断 emoji 代理对")
    void customerSend_longContent_previewTruncatedSafely() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        when(chatSessionMapper.selectOne(any(), anyBoolean())).thenReturn(session(100L, 5L, 1L, null));
        when(chatMessageMapper.insert(anyMsg())).thenReturn(1);
        when(chatSessionMapper.updateById(anySession())).thenReturn(1);

        // 第 120 个字符（index 119）放一个 emoji（代理对），其前 119 个为普通字符
        String content = "a".repeat(119) + "😀" + "tail".repeat(50);
        chatService.sendFromCustomer(CUSTOMER, custReq(5L, content));

        org.mockito.ArgumentCaptor<ChatSession> captor = org.mockito.ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).updateById((ChatSession) captor.capture());
        String preview = captor.getValue().getLastMessage();
        // 截断到不超过 120，且结尾不是半个代理字符（整个 emoji 被一起丢弃）
        assertEquals(119, preview.length());
        assertFalse(Character.isHighSurrogate(preview.charAt(preview.length() - 1)));
        assertTrue(preview.chars().allMatch(c -> c == 'a'));
    }

    @Test
    @DisplayName("买家发消息：恰好 120 字不截断")
    void customerSend_exactly120_notTruncated() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        when(chatSessionMapper.selectOne(any(), anyBoolean())).thenReturn(session(100L, 5L, 1L, null));
        when(chatMessageMapper.insert(anyMsg())).thenReturn(1);
        when(chatSessionMapper.updateById(anySession())).thenReturn(1);

        String content = "b".repeat(120);
        chatService.sendFromCustomer(CUSTOMER, custReq(5L, content));

        org.mockito.ArgumentCaptor<ChatSession> captor = org.mockito.ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).updateById((ChatSession) captor.capture());
        assertEquals(120, captor.getValue().getLastMessage().length());
    }

    private static com.twentyzhang.bluewhale.dto.chat.StaffSendRequest staffReq(Long sessionId, String content) {
        com.twentyzhang.bluewhale.dto.chat.StaffSendRequest r = new com.twentyzhang.bluewhale.dto.chat.StaffSendRequest();
        r.setSessionId(sessionId);
        r.setContent(content);
        return r;
    }

    @Test
    @DisplayName("客服回复：是 assignee 时投递买家")
    void staffSend_asAssignee_sendsToCustomer() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 5L, 1L, 9L));
        when(chatMessageMapper.insert(anyMsg())).thenReturn(1);
        when(chatSessionMapper.updateById(anySession())).thenReturn(1);

        chatService.sendFromStaff(STAFF, staffReq(100L, "您好"));

        verify(messagingTemplate).convertAndSendToUser(eq("1"), eq("/queue/messages"), any());
    }

    @Test
    @DisplayName("客服回复：非 assignee 抛 403")
    void staffSend_notAssignee_throws403() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 5L, 1L, 8L));

        var ex = assertThrows(com.twentyzhang.bluewhale.exception.BusinessException.class,
                () -> chatService.sendFromStaff(STAFF, staffReq(100L, "x")));
        assertEquals(com.twentyzhang.bluewhale.common.Result.CODE_FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("认领成功：写库 + 广播 CLAIMED")
    void claim_success_broadcasts() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 5L, 1L, null));
        when(chatSessionMapper.claim(100L, 9L)).thenReturn(1);
        when(userMapper.selectById(9L)).thenReturn(
                com.twentyzhang.bluewhale.entity.User.builder().id(9L).nickname("小蓝").build());

        var resp = chatService.claim(STAFF, 100L);

        assertEquals(9L, resp.getAssigneeStaffId());
        verify(messagingTemplate).convertAndSend(eq("/topic/store.5"),
                any(com.twentyzhang.bluewhale.dto.chat.StoreTopicEvent.class));
    }

    @Test
    @DisplayName("认领失败：已被他人接待抛错且不广播")
    void claim_alreadyClaimed_throws() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L))
                .thenReturn(session(100L, 5L, 1L, null))
                .thenReturn(session(100L, 5L, 1L, 8L));
        when(chatSessionMapper.claim(100L, 9L)).thenReturn(0);
        when(userMapper.selectById(8L)).thenReturn(
                com.twentyzhang.bluewhale.entity.User.builder().id(8L).nickname("阿强").build());

        var ex = assertThrows(com.twentyzhang.bluewhale.exception.BusinessException.class,
                () -> chatService.claim(STAFF, 100L));
        assertTrue(ex.getMessage().contains("阿强"));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("释放成功：广播 RELEASED")
    void release_success_broadcasts() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 5L, 1L, 9L));
        when(chatSessionMapper.release(100L, 9L)).thenReturn(1);

        chatService.release(STAFF, 100L);

        verify(messagingTemplate).convertAndSend(eq("/topic/store.5"),
                any(com.twentyzhang.bluewhale.dto.chat.StoreTopicEvent.class));
    }

    @Test
    @DisplayName("释放失败：非接待人抛 403")
    void release_notOwner_throws403() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 5L, 1L, 8L));
        when(chatSessionMapper.release(100L, 9L)).thenReturn(0);

        var ex = assertThrows(com.twentyzhang.bluewhale.exception.BusinessException.class,
                () -> chatService.release(STAFF, 100L));
        assertEquals(com.twentyzhang.bluewhale.common.Result.CODE_FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("释放：会话属于别的店铺时抛 403（按店校验）")
    void release_otherStore_throws403() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 7L, 1L, 9L)); // 店铺 7 ≠ staff 店铺 5

        var ex = assertThrows(com.twentyzhang.bluewhale.exception.BusinessException.class,
                () -> chatService.release(STAFF, 100L));
        assertEquals(com.twentyzhang.bluewhale.common.Result.CODE_FORBIDDEN, ex.getCode());
        verify(chatSessionMapper, never()).release(anyLong(), anyLong());
    }

    @Test
    @DisplayName("认领失败：竞态下会话刚被释放（assignee=null）时给出通用提示，不抛 NPE")
    void claim_lostRaceThenReleased_genericName() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L))
                .thenReturn(session(100L, 5L, 1L, null))   // requireSession：未接入
                .thenReturn(session(100L, 5L, 1L, null));  // claim 0 行后重查：已被释放，assignee=null
        when(chatSessionMapper.claim(100L, 9L)).thenReturn(0);

        var ex = assertThrows(com.twentyzhang.bluewhale.exception.BusinessException.class,
                () -> chatService.claim(STAFF, 100L));
        assertTrue(ex.getMessage().contains("其他客服"));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
