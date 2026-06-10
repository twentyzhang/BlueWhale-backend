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

    @Test
    @DisplayName("会话列表：客服按本店查询并标记买家在线")
    void listSessions_staff_marksCustomerOnline() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectList(any())).thenReturn(java.util.List.of(session(100L, 5L, 1L, 9L)));
        when(userMapper.selectById(9L)).thenReturn(
                com.twentyzhang.bluewhale.entity.User.builder().id(9L).nickname("小蓝").build());
        when(redisUtil.sIsMember(anyString(), eq("1"))).thenReturn(true);

        var list = chatService.listSessions(STAFF);

        assertEquals(1, list.size());
        assertTrue(list.get(0).isPeerOnline());
        assertEquals("小蓝", list.get(0).getAssigneeName());
    }

    @Test
    @DisplayName("会话列表：买家视角填店名与本店客服在线")
    void listSessions_customer_fillsStoreNameAndOnline() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        when(chatSessionMapper.selectList(any())).thenReturn(java.util.List.of(session(100L, 5L, 1L, null)));
        when(storeMapper.selectById(5L)).thenReturn(
                com.twentyzhang.bluewhale.entity.Store.builder().id(5L).name("蓝鲸店").build());
        when(redisUtil.sCard(anyString())).thenReturn(2L);

        var list = chatService.listSessions(CUSTOMER);

        assertEquals(1, list.size());
        assertEquals("蓝鲸店", list.get(0).getStoreName());
        assertTrue(list.get(0).isPeerOnline());
        assertNull(list.get(0).getAssigneeName());
    }

    @Test
    @DisplayName("历史消息：买家查看本人会话成功")
    void getMessages_customerOwnSession_ok() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 5L, 1L, 9L));
        when(chatMessageMapper.selectList(any())).thenReturn(java.util.List.of(
                com.twentyzhang.bluewhale.entity.ChatMessage.builder()
                        .id(7L).sessionId(100L).senderRole("CUSTOMER").senderId(1L).content("hi").build()));

        var msgs = chatService.getMessages(CUSTOMER, 100L, null, 20);

        assertEquals(1, msgs.size());
        assertEquals(7L, msgs.get(0).getId());
    }

    @Test
    @DisplayName("历史消息：买家查看他人会话抛 403")
    void getMessages_customerOtherSession_throws403() {
        mockAuthUser(2L, AuthUtil.ROLE_CUSTOMER, null);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 5L, 1L, 9L)); // 属于客户 1
        AuthUser other = new AuthUser(2L, AuthUtil.ROLE_CUSTOMER, null);

        var ex = assertThrows(com.twentyzhang.bluewhale.exception.BusinessException.class,
                () -> chatService.getMessages(other, 100L, null, 20));
        assertEquals(com.twentyzhang.bluewhale.common.Result.CODE_FORBIDDEN, ex.getCode());
    }

    @Test
    @DisplayName("历史消息：客服查看本店会话成功（按店读，即便接待人是别人）")
    void getMessages_staffSameStore_ok() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 5L, 1L, 8L)); // 接待人是别人
        when(chatMessageMapper.selectList(any())).thenReturn(java.util.List.of());

        var msgs = chatService.getMessages(STAFF, 100L, null, 20);
        assertTrue(msgs.isEmpty());
    }

    @Test
    @DisplayName("历史消息：客服查看别店会话抛 403（按店读）")
    void getMessages_staffOtherStore_throws403() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 7L, 1L, 8L)); // 店铺 7 ≠ staff 店铺 5

        var ex = assertThrows(com.twentyzhang.bluewhale.exception.BusinessException.class,
                () -> chatService.getMessages(STAFF, 100L, null, 20));
        assertEquals(com.twentyzhang.bluewhale.common.Result.CODE_FORBIDDEN, ex.getCode());
        verify(chatMessageMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("历史消息：会话不存在抛 404")
    void getMessages_notFound_throws404() {
        mockAuthUser(1L, AuthUtil.ROLE_CUSTOMER, null);
        when(chatSessionMapper.selectById(999L)).thenReturn(null);

        var ex = assertThrows(com.twentyzhang.bluewhale.exception.BusinessException.class,
                () -> chatService.getMessages(CUSTOMER, 999L, null, 20));
        assertEquals(com.twentyzhang.bluewhale.common.Result.CODE_NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("autoReleaseOfflineAssignees：离线客服的会话释放并广播，在线的跳过（B1）")
    void autoRelease_releasesOfflineAssigneeSessions() {
        // 会话101 归属客服9（store5）离线 → 释放；会话102 归属客服8（store5）在线 → 跳过
        ChatSession s1 = session(101L, 5L, 1L, 9L);
        ChatSession s2 = session(102L, 5L, 2L, 8L);
        when(chatSessionMapper.selectClaimedSessions()).thenReturn(java.util.List.of(s1, s2));
        when(redisUtil.sIsMember("cs:online:store:5", "9")).thenReturn(false);  // 离线
        when(redisUtil.sIsMember("cs:online:store:5", "8")).thenReturn(true);   // 在线
        when(chatSessionMapper.release(101L, 9L)).thenReturn(1);

        int released = chatService.autoReleaseOfflineAssignees();

        assertEquals(1, released);
        verify(chatSessionMapper).release(101L, 9L);
        verify(chatSessionMapper, never()).release(eq(102L), anyLong());
        verify(messagingTemplate).convertAndSend(eq("/topic/store.5"), any(StoreTopicEvent.class));
    }
}
