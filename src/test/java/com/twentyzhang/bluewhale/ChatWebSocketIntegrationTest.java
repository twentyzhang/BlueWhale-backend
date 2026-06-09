package com.twentyzhang.bluewhale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.dto.chat.CustomerSendRequest;
import com.twentyzhang.bluewhale.dto.chat.StaffSendRequest;
import com.twentyzhang.bluewhale.dto.chat.StoreTopicEvent;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket / STOMP 端到端集成测试。
 *
 * 收帧策略：STOMP 帧中 payload 由服务端序列化为 JSON 字符串传来；
 * 客户端侧 StompFrameHandler 声明 payloadType=String，接收后用 ObjectMapper 解析。
 * 这样完全绕开了 Lombok @Builder/@Data 类无公共无参构造器时 Jackson 反序列化失败的问题，
 * 同时保证断言精确覆盖业务字段，不依赖对象状态。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ChatWebSocket 集成测试")
class ChatWebSocketIntegrationTest {

    @LocalServerPort int port;
    @Autowired JwtUtil jwtUtil;
    @Autowired ObjectMapper objectMapper;

    private WebSocketStompClient client;

    @BeforeEach
    void setUp() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(converter);
    }

    private StompSession connect(String jwt) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwt);
        return client.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    /**
     * 订阅目的地，每帧 payload 以 Map 形式入队（使用 Jackson 反序列化 JSON 对象到 Map，
     * 绕过 Lombok @Builder/@Data 无公共无参构造器的反序列化问题）。
     */
    @SuppressWarnings("unchecked")
    private static BlockingQueue<Map<String, Object>> subscribeAsMap(StompSession session, String dest) {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(dest, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                if (payload != null) queue.add((Map<String, Object>) payload);
            }
        });
        return queue;
    }

    private ResponseEntity<String> rest(String jwt, HttpMethod method, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return new RestTemplate().exchange("http://localhost:" + port + path, method,
                new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("非法 token 连接被拒绝")
    void invalidToken_rejected() {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer invalid.token.value");
        assertThrows(Exception.class, () ->
                client.connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("完整链路：买家发→店铺主题→客服认领→买家再发→assignee收→客服回→买家收→历史可读")
    void fullChatFlow() throws Exception {
        // 唯一买家 ID，保证会话初始未接入（避免与历史运行的已接入会话冲突）
        long customerId = 1_000_000 + (System.nanoTime() % 1_000_000_000L);
        long storeId = 7_777_777L;
        long staffId = 8_888_888L;
        String custJwt = jwtUtil.generateToken(customerId, AuthUtil.ROLE_CUSTOMER, null);
        String staffJwt = jwtUtil.generateToken(staffId, AuthUtil.ROLE_STAFF, storeId);

        StompSession customer = connect(custJwt);
        StompSession staff = connect(staffJwt);

        BlockingQueue<Map<String, Object>> custInbox  = subscribeAsMap(customer, "/user/queue/messages");
        BlockingQueue<Map<String, Object>> storeFeed  = subscribeAsMap(staff, "/topic/store." + storeId);
        BlockingQueue<Map<String, Object>> staffInbox = subscribeAsMap(staff, "/user/queue/messages");
        Thread.sleep(1000); // 等订阅在 broker 注册

        // 1. 买家首条（未接入）→ 客服在店铺主题收到 MESSAGE 事件
        //    这一步也验证了关键修复：WS 线程无 SecurityContext 时鉴权不再误判 401
        CustomerSendRequest m1 = new CustomerSendRequest();
        m1.setStoreId(storeId); m1.setContent("你好，在吗");
        customer.send("/app/chat.customer.send", m1);

        Map<String, Object> ev = storeFeed.poll(10, TimeUnit.SECONDS);
        assertNotNull(ev, "客服应在店铺主题收到未接入新消息");
        assertEquals(StoreTopicEvent.TYPE_MESSAGE, ev.get("type"));
        long sessionId = ((Number) ev.get("sessionId")).longValue();
        assertTrue(sessionId > 0, "sessionId 应为正整数");
        @SuppressWarnings("unchecked")
        Map<String, Object> msgMap = (Map<String, Object>) ev.get("message");
        assertNotNull(msgMap, "message 字段应存在");
        assertEquals("你好，在吗", msgMap.get("content"));

        // 2. 客服 REST 认领
        ResponseEntity<String> claimResp = rest(staffJwt, HttpMethod.POST,
                "/api/chat/sessions/" + sessionId + "/claim");
        assertTrue(claimResp.getStatusCode().is2xxSuccessful(), "认领应成功: " + claimResp.getBody());

        // 3. 买家再发（已接入）→ 仅 assignee 私信收到
        CustomerSendRequest m2 = new CustomerSendRequest();
        m2.setStoreId(storeId); m2.setContent("我要退货");
        customer.send("/app/chat.customer.send", m2);

        Map<String, Object> toStaff = staffInbox.poll(10, TimeUnit.SECONDS);
        assertNotNull(toStaff, "已接入后买家消息应私投给接待客服");
        assertEquals("我要退货", toStaff.get("content"));

        // 4. 客服回复 → 买家私信收到
        StaffSendRequest reply = new StaffSendRequest();
        reply.setSessionId(sessionId); reply.setContent("好的，已为您受理");
        staff.send("/app/chat.staff.send", reply);

        Map<String, Object> toCust = custInbox.poll(10, TimeUnit.SECONDS);
        assertNotNull(toCust, "买家应收到客服回复");
        assertEquals("好的，已为您受理", toCust.get("content"));
        assertEquals("STAFF", toCust.get("senderRole"));

        // 5. 历史：客服按店读，应含全部三条
        ResponseEntity<String> history = rest(staffJwt, HttpMethod.GET,
                "/api/chat/sessions/" + sessionId + "/messages?size=50");
        assertTrue(history.getStatusCode().is2xxSuccessful());
        String body = history.getBody();
        assertNotNull(body);
        assertTrue(body.contains("你好，在吗"), "历史应含首条");
        assertTrue(body.contains("我要退货"), "历史应含第二条");
        assertTrue(body.contains("好的，已为您受理"), "历史应含客服回复");
    }
}
