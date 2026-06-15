package com.twentyzhang.bluewhale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.entity.Order;
import com.twentyzhang.bluewhale.entity.User;
import com.twentyzhang.bluewhale.mapper.OrderMapper;
import com.twentyzhang.bluewhale.mapper.UserMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 任务 D：模拟支付异步回路端到端（需 MySQL + Redis）。
 * 注册/登录一个顾客 → 直接插一笔待支付订单 → 发起支付 → 模拟收银台成功/失败
 * → 轮询查单直到终态，断言订单状态相应推进。
 * <p>基础设施（MySQL / Redis）不可用时，登录拿不到 token，用 {@code assumeTrue} 优雅跳过，
 * 不阻断纯单测套件。callback-delay-ms 默认 1.5s，轮询窗口覆盖之。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("模拟支付异步回路")
class PaymentFlowIntegrationTest {

    private static final String PHONE = "13900000007";
    private static final String PASSWORD = "Pay@123456";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private UserMapper userMapper;

    /** 注册（已存在则忽略）并登录，返回 access token；基础设施不可用时返回 null。 */
    private String registerAndLogin() {
        try {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"" + PHONE + "\",\"password\":\"" + PASSWORD
                            + "\",\"nickname\":\"支付测试顾客\",\"role\":\"CUSTOMER\"}"));
        } catch (Exception ignored) {
            // 已注册 / 注册路径异常都不致命，继续尝试登录
        }
        try {
            String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + PHONE + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andReturn().getResponse().getContentAsString();
            JsonNode data = objectMapper.readTree(body).get("data");
            return data != null && data.has("token") ? data.get("token").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 直接插一笔属于该顾客、状态 PENDING_PAYMENT 的订单，返回订单 ID。 */
    private Long insertPendingOrder(Long userId) {
        Order order = Order.builder()
                .userId(userId)
                .storeId(1L)                       // 种子店铺 南鲸旗舰店
                .status("PENDING_PAYMENT")
                .totalAmount(new BigDecimal("9.90"))
                .discountAmount(BigDecimal.ZERO)
                .payableAmount(new BigDecimal("9.90"))
                .addrReceiverName("PAY_TEST")      // 便于排查的可识别标记
                .addrPhone("13900000007")
                .addrProvince("江苏省")
                .addrCity("南京市")
                .addrDistrict("玄武区")
                .addrDetail("测试路 1 号")
                .deleted(0)
                .build();
        orderMapper.insert(order);
        return order.getId();
    }

    private String tradeNoFromPay(Long orderId, String token) throws Exception {
        String body = mvc.perform(post("/api/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("tradeNo").asText();
    }

    /** 轮询查单直到终态或超时；返回最终 status（PENDING/SUCCESS/FAILED）。 */
    private String pollUntilTerminal(String tradeNo, String token) throws Exception {
        for (int i = 0; i < 25; i++) {
            String body = mvc.perform(get("/api/payments/" + tradeNo)
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse().getContentAsString();
            String status = objectMapper.readTree(body).get("data").get("status").asText();
            if (!"PENDING".equals(status)) {
                return status;
            }
            Thread.sleep(200);
        }
        return "PENDING";
    }

    private String orderStatus(Long orderId) {
        return orderMapper.selectById(orderId).getStatus();
    }

    @Test
    @DisplayName("发起→收银台成功→轮询查单 SUCCESS，订单置 PAID")
    void payFlow_success() throws Exception {
        String token = registerAndLogin();
        Assumptions.assumeTrue(token != null, "基础设施（MySQL/Redis）不可用，跳过支付流程集成测试");

        User user = userMapper.selectByPhone(PHONE);
        Long orderId = insertPendingOrder(user.getId());

        String tradeNo = tradeNoFromPay(orderId, token);
        mvc.perform(post("/api/mock-pay/" + tradeNo + "/success"));   // 开放端点

        assertEquals("SUCCESS", pollUntilTerminal(tradeNo, token));
        assertEquals("PAID", orderStatus(orderId));
    }

    @Test
    @DisplayName("发起→收银台失败→轮询查单 FAILED，订单仍 PENDING_PAYMENT")
    void payFlow_failed() throws Exception {
        String token = registerAndLogin();
        Assumptions.assumeTrue(token != null, "基础设施（MySQL/Redis）不可用，跳过支付流程集成测试");

        User user = userMapper.selectByPhone(PHONE);
        Long orderId = insertPendingOrder(user.getId());

        String tradeNo = tradeNoFromPay(orderId, token);
        mvc.perform(post("/api/mock-pay/" + tradeNo + "/fail"));

        assertEquals("FAILED", pollUntilTerminal(tradeNo, token));
        assertEquals("PENDING_PAYMENT", orderStatus(orderId));
    }
}
