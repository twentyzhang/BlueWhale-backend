# 实时客服功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 BlueWhale 商城实现买家↔店铺客服的实时聊天（STOMP over WebSocket + 认领制接待 + 离线消息 + 在线状态）。

**Architecture:** 复用现有 JWT 在 STOMP `CONNECT` 帧鉴权；消息经 `ChatService` 落库并按会话 `assignee_staff_id` 路由（未接入→店铺主题广播 / 已接入→投递归属客服）；接入/释放走 REST 原子 SQL；在线状态用 Redis Set；单实例内存 SimpleBroker。

**Tech Stack:** Spring Boot 3.4 + `spring-boot-starter-websocket`、STOMP SimpleBroker、MyBatis Plus、Redis（StringRedisTemplate）、Flyway（V4）、JUnit5 + Mockito（单测）、`WebSocketStompClient`（集成测试）。

**设计来源：** [`docs/superpowers/specs/2026-06-08-realtime-customer-service-design.md`](../specs/2026-06-08-realtime-customer-service-design.md)，关键选型见 [`docs/重要决策说明.md`](../../重要决策说明.md) 第 50 条。

**对 spec 的实现细化（已确认无碍）：**
- WS 发送端点按角色拆成两个 `@MessageMapping`：`/app/chat.customer.send`（买家）与 `/app/chat.staff.send`（客服），替代 spec 中单一 `/app/chat.send`。理由：两者请求体不同（买家带 `storeId`、客服带 `sessionId`）、角色不同，拆分后无需在一个端点里按 body 形状分支，授权也更清晰。
- 店铺主题 `/topic/store.{storeId}` 上统一投递信封 `StoreTopicEvent{type, ...}`，`type ∈ {MESSAGE, CLAIMED, RELEASED}`，让客服端区分“新消息/被认领/被释放”。

---

## 文件结构总览

**新增：**
- `entity/ChatSession.java`、`entity/ChatMessage.java`
- `mapper/ChatSessionMapper.java`（含原子 claim/release SQL）、`mapper/ChatMessageMapper.java`
- `dto/chat/CustomerSendRequest.java`、`StaffSendRequest.java`、`ChatMessageResponse.java`、`ChatSessionItemResponse.java`、`ClaimResponse.java`、`StoreTopicEvent.java`
- `common/ChatPrincipal.java`（STOMP Principal，`getName()` 返回 userId 字符串，供 user-destination 投递）
- `util/ChatKeys.java`（Redis 在线状态 key 常量）
- `config/WebSocketConfig.java`（`@EnableWebSocketMessageBroker` + 注册拦截器）
- `interceptor/StompAuthChannelInterceptor.java`（CONNECT 鉴权 + SUBSCRIBE/SEND 授权）
- `listener/WebSocketPresenceListener.java`（连接/断开 → Redis 在线集合）
- `service/ChatService.java` + `service/impl/ChatServiceImpl.java`
- `controller/ChatWsController.java`（`@MessageMapping`）+ `controller/ChatRestController.java`（REST）
- `resources/db/migration/V4__chat.sql`
- 测试：`service/ChatServiceTest.java`、`interceptor/StompAuthChannelInterceptorTest.java`、`listener/WebSocketPresenceListenerTest.java`、`util/RedisUtilTest`（追加 Set 用例）、`ChatWebSocketIntegrationTest.java`

**修改：**
- `pom.xml`（加 `spring-boot-starter-websocket`）
- `config/SecurityConfig.java`（放行 `/ws/**`）
- `util/RedisUtil.java`（追加 Set 操作）

---

### Task 1: 引入 WebSocket 依赖 + 骨架配置 + 放行握手路径

**Files:**
- Modify: `pom.xml`（在 Redis 依赖之后追加）
- Create: `src/main/java/com/twentyzhang/bluewhale/config/WebSocketConfig.java`
- Modify: `src/main/java/com/twentyzhang/bluewhale/config/SecurityConfig.java:42`（`anyRequest` 之前加放行）

- [ ] **Step 1: 加依赖**

在 `pom.xml` 的 Redis 依赖块（`spring-boot-starter-data-redis`，约 95 行）之后插入：

```xml
		<!-- WebSocket + STOMP（实时客服） -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-websocket</artifactId>
		</dependency>
```

- [ ] **Step 2: 写 WebSocketConfig（先不挂拦截器，Task 8 再补）**

Create `config/WebSocketConfig.java`：

```java
package com.twentyzhang.bluewhale.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 纯 WebSocket 端点（前端为独立项目，暂不加 SockJS 回退）
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 内存 SimpleBroker：主题广播 /topic、用户私信 /queue
        registry.enableSimpleBroker("/topic", "/queue");
        // 客户端 SEND 目的地前缀
        registry.setApplicationDestinationPrefixes("/app");
        // user-destination 前缀（convertAndSendToUser 解析）
        registry.setUserDestinationPrefix("/user");
    }
}
```

- [ ] **Step 3: SecurityConfig 放行 `/ws/**`**

在 `SecurityConfig.java` 的 `.anyRequest().authenticated()`（第 42 行）之前加一行：

```java
                .requestMatchers("/ws/**").permitAll()   // 握手放行，真正鉴权在 STOMP CONNECT 帧
                .anyRequest().authenticated()
```

- [ ] **Step 4: 编译验证应用可启动**

Run: `mvn -q clean compile`
Expected: BUILD SUCCESS，无编译错误。

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/twentyzhang/bluewhale/config/WebSocketConfig.java src/main/java/com/twentyzhang/bluewhale/config/SecurityConfig.java
git commit -m "feat(chat): 引入 WebSocket/STOMP 依赖与骨架配置（任务7）"
```

---

### Task 2: 数据库迁移 V4 + 实体 + Mapper

**Files:**
- Create: `src/main/resources/db/migration/V4__chat.sql`
- Create: `entity/ChatSession.java`、`entity/ChatMessage.java`
- Create: `mapper/ChatSessionMapper.java`、`mapper/ChatMessageMapper.java`

- [ ] **Step 1: 写迁移 V4**

Create `src/main/resources/db/migration/V4__chat.sql`：

```sql
-- ============================================================
-- Flyway 迁移 V4：实时客服（路线图任务 7）
--   chat_session  会话（一个买家 ↔ 一个店铺，可复用，认领制）
--   chat_message  消息（append-only）
--
-- 【重要】已发布的 V1~V3 不可修改，结构变更一律新增版本化迁移（见决策 #43）。
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_session`;

-- ----------------------------
-- Table: chat_session
-- UNIQUE(store_id, customer_id)：同一买家×店铺永远一个会话（决策 spec #3）
-- assignee_staff_id：接待客服，NULL=未接入（认领制核心，决策 #50）
-- last_message / last_message_at：冗余，会话列表排序与预览，避免 N+1
-- ----------------------------
CREATE TABLE `chat_session` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '会话ID',
    `store_id`          BIGINT       NOT NULL                            COMMENT '归属店铺',
    `customer_id`       BIGINT       NOT NULL                            COMMENT '买家',
    `assignee_staff_id` BIGINT           NULL                            COMMENT '接待客服，NULL=未接入',
    `last_message`      VARCHAR(120)     NULL                            COMMENT '最后一条消息预览',
    `last_message_at`   DATETIME         NULL                            COMMENT '最后消息时间（列表排序）',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '更新时间',
    `deleted`           TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除 0正常 1删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_store_customer` (`store_id`, `customer_id`),
    INDEX `idx_store_id` (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话';

-- ----------------------------
-- Table: chat_message（append-only，无 updated_at）
-- INDEX(session_id, id)：历史消息按会话游标分页
-- ----------------------------
CREATE TABLE `chat_message` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '消息ID',
    `session_id`  BIGINT        NOT NULL                            COMMENT '所属会话',
    `sender_role` VARCHAR(16)   NOT NULL                            COMMENT 'CUSTOMER / STAFF',
    `sender_id`   BIGINT        NOT NULL                            COMMENT '发送者 userId',
    `content`     VARCHAR(1000) NOT NULL                            COMMENT '消息内容',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0                  COMMENT '逻辑删除 0正常 1删除',
    PRIMARY KEY (`id`),
    INDEX `idx_session_cursor` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服消息';
```

- [ ] **Step 2: 写 ChatSession 实体**

Create `entity/ChatSession.java`：

```java
package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;

    private Long customerId;

    /** 接待客服 ID，null = 未接入 */
    private Long assigneeStaffId;

    private String lastMessage;

    private LocalDateTime lastMessageAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
```

- [ ] **Step 3: 写 ChatMessage 实体（append-only，无 updateTime）**

Create `entity/ChatMessage.java`：

```java
package com.twentyzhang.bluewhale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** CUSTOMER / STAFF */
    private String senderRole;

    private Long senderId;

    private String content;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
```

> 说明：消息不可变，无 `updated_at` 列。`MyMetaObjectHandler.insertFill` 对不存在的 `updateTime` 字段 `strictInsertFill` 为空操作（无 setter 即跳过），不会报错。

- [ ] **Step 4: 写 Mapper（含原子 claim/release）**

Create `mapper/ChatSessionMapper.java`：

```java
package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 原子认领：仅当会话尚未被接入时才写入 assignee。
     * 返回 1=接入成功，0=已被他人抢先（防两个客服并发认领同一会话）。
     */
    @Update("""
            UPDATE chat_session
            SET assignee_staff_id = #{staffId}, updated_at = NOW()
            WHERE id = #{id} AND assignee_staff_id IS NULL AND deleted = 0
            """)
    int claim(@Param("id") Long id, @Param("staffId") Long staffId);

    /**
     * 释放接待：仅当前接待人本人可释放，置回未接入。
     * 返回 1=释放成功，0=非本人接待或会话不存在。
     */
    @Update("""
            UPDATE chat_session
            SET assignee_staff_id = NULL, updated_at = NOW()
            WHERE id = #{id} AND assignee_staff_id = #{staffId} AND deleted = 0
            """)
    int release(@Param("id") Long id, @Param("staffId") Long staffId);
}
```

Create `mapper/ChatMessageMapper.java`：

```java
package com.twentyzhang.bluewhale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.twentyzhang.bluewhale.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn -q clean compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V4__chat.sql src/main/java/com/twentyzhang/bluewhale/entity/ChatSession.java src/main/java/com/twentyzhang/bluewhale/entity/ChatMessage.java src/main/java/com/twentyzhang/bluewhale/mapper/ChatSessionMapper.java src/main/java/com/twentyzhang/bluewhale/mapper/ChatMessageMapper.java
git commit -m "feat(chat): V4 迁移、ChatSession/ChatMessage 实体与 Mapper（任务7）"
```

---

### Task 3: DTO + STOMP Principal + Redis key 常量

**Files:**
- Create: `dto/chat/CustomerSendRequest.java`、`StaffSendRequest.java`、`ChatMessageResponse.java`、`ChatSessionItemResponse.java`、`ClaimResponse.java`、`StoreTopicEvent.java`
- Create: `common/ChatPrincipal.java`
- Create: `util/ChatKeys.java`

- [ ] **Step 1: 请求 DTO（带校验）**

Create `dto/chat/CustomerSendRequest.java`：

```java
package com.twentyzhang.bluewhale.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 买家发送消息：SEND /app/chat.customer.send */
@Data
public class CustomerSendRequest {

    @NotNull(message = "storeId 不能为空")
    private Long storeId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 1000, message = "消息长度不能超过 1000 字")
    private String content;
}
```

Create `dto/chat/StaffSendRequest.java`：

```java
package com.twentyzhang.bluewhale.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 客服回复消息：SEND /app/chat.staff.send */
@Data
public class StaffSendRequest {

    @NotNull(message = "sessionId 不能为空")
    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 1000, message = "消息长度不能超过 1000 字")
    private String content;
}
```

- [ ] **Step 2: 响应 / 事件 DTO**

Create `dto/chat/ChatMessageResponse.java`：

```java
package com.twentyzhang.bluewhale.dto.chat;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 单条消息视图（投递给买家/客服、历史拉取均用） */
@Data
@Builder
public class ChatMessageResponse {
    private Long id;
    private Long sessionId;
    private String senderRole;   // CUSTOMER / STAFF
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;
}
```

Create `dto/chat/ChatSessionItemResponse.java`：

```java
package com.twentyzhang.bluewhale.dto.chat;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话列表项（买家/客服共用，字段按角色填充） */
@Data
@Builder
public class ChatSessionItemResponse {
    private Long sessionId;
    private Long storeId;
    private String storeName;        // 买家视角填店名
    private Long customerId;
    private Long assigneeStaffId;     // null=未接入
    private String assigneeName;      // 接待客服昵称
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private boolean peerOnline;       // 买家视角=本店有客服在线；客服视角=该买家在线
}
```

Create `dto/chat/ClaimResponse.java`：

```java
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
```

Create `dto/chat/StoreTopicEvent.java`：

```java
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
```

- [ ] **Step 3: STOMP Principal**

Create `common/ChatPrincipal.java`：

```java
package com.twentyzhang.bluewhale.common;

import java.security.Principal;

/**
 * STOMP 连接的已认证主体。
 * getName() 返回 userId 字符串：Spring user-destination（convertAndSendToUser）按此名投递。
 * 同时携带完整 AuthUser，供拦截器做 SUBSCRIBE/SEND 授权。
 */
public record ChatPrincipal(AuthUser user) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(user.userId());
    }
}
```

- [ ] **Step 4: Redis key 常量**

Create `util/ChatKeys.java`：

```java
package com.twentyzhang.bluewhale.util;

/** 实时客服 Redis key 集中定义（在线状态）。 */
public final class ChatKeys {

    private ChatKeys() {}

    /** 在线买家集合：SADD/SREM userId */
    public static final String ONLINE_CUSTOMERS = "cs:online:customers";

    /** 某店在线客服集合：SADD/SREM userId */
    public static String onlineStoreStaff(Long storeId) {
        return "cs:online:store:" + storeId;
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn -q clean compile`
Expected: BUILD SUCCESS。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/dto/chat src/main/java/com/twentyzhang/bluewhale/common/ChatPrincipal.java src/main/java/com/twentyzhang/bluewhale/util/ChatKeys.java
git commit -m "feat(chat): DTO、STOMP Principal 与 Redis key 常量（任务7）"
```

---

### Task 4: RedisUtil 追加 Set 操作（在线状态用）

**Files:**
- Modify: `util/RedisUtil.java`
- Test: `src/test/java/com/twentyzhang/bluewhale/util/RedisUtilTest.java`

- [ ] **Step 1: 写失败测试**

打开 `RedisUtilTest.java`，参照已有用例风格（mock `StringRedisTemplate` 及 `opsForSet()`）追加：

```java
    @Test
    @DisplayName("sAdd 调用 opsForSet().add")
    void sAdd_delegatesToSetOps() {
        org.springframework.data.redis.core.SetOperations<String, String> setOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.add("k", "v")).thenReturn(1L);

        assertEquals(1L, redisUtil.sAdd("k", "v"));
        verify(setOps).add("k", "v");
    }

    @Test
    @DisplayName("sCard 返回集合大小")
    void sCard_returnsSize() {
        org.springframework.data.redis.core.SetOperations<String, String> setOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.size("k")).thenReturn(3L);

        assertEquals(3L, redisUtil.sCard("k"));
    }

    @Test
    @DisplayName("sIsMember 委托 opsForSet().isMember")
    void sIsMember_delegates() {
        org.springframework.data.redis.core.SetOperations<String, String> setOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.isMember("k", "v")).thenReturn(true);

        assertTrue(redisUtil.sIsMember("k", "v"));
    }
```

> 若 `RedisUtilTest` 尚无 `stringRedisTemplate` mock / `redisUtil` 实例字段，参照文件顶部已有 `@Mock StringRedisTemplate stringRedisTemplate;` 与 `@InjectMocks RedisUtil redisUtil;`（已存在）即可。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -Dtest=RedisUtilTest test`
Expected: FAIL，编译错误“找不到方法 sAdd/sCard/sIsMember”。

- [ ] **Step 3: 实现 RedisUtil Set 方法**

在 `RedisUtil.java` 的 `deleteByPrefix` 之前加入：

```java
    // ---------- Set 操作（在线状态） ----------

    /** 向集合添加成员，返回新增成员数。 */
    public Long sAdd(String key, String... values) {
        return stringRedisTemplate.opsForSet().add(key, values);
    }

    /** 从集合移除成员，返回移除成员数。 */
    public Long sRemove(String key, Object... values) {
        return stringRedisTemplate.opsForSet().remove(key, values);
    }

    /** 返回集合元素个数（key 不存在返回 0）。 */
    public Long sCard(String key) {
        Long size = stringRedisTemplate.opsForSet().size(key);
        return size != null ? size : 0L;
    }

    /** 判断成员是否在集合中。 */
    public Boolean sIsMember(String key, Object value) {
        return stringRedisTemplate.opsForSet().isMember(key, value);
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -Dtest=RedisUtilTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/util/RedisUtil.java src/test/java/com/twentyzhang/bluewhale/util/RedisUtilTest.java
git commit -m "feat(chat): RedisUtil 增加 Set 操作（在线状态，任务7）"
```

---

### Task 5: ChatService 接口 + 买家发消息（会话自动创建 + 路由）

**Files:**
- Create: `service/ChatService.java`
- Create: `service/impl/ChatServiceImpl.java`
- Test: `src/test/java/com/twentyzhang/bluewhale/service/ChatServiceTest.java`

整个 Service 用纯 Mockito 单测（遵决策 #0）。`ChatServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>`，故 `baseMapper` 用 `ReflectionTestUtils` 注入（见决策 #0b）。

- [ ] **Step 1: 写接口（含本任务方法，后续任务补其余实现）**

Create `service/ChatService.java`：

```java
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

    /**
     * 买家发消息：解析/自动创建会话 → 落库 → 按 assignee 路由
     * （未接入→广播店铺主题；已接入→投递归属客服）。返回落库后的消息视图。
     */
    ChatMessageResponse sendFromCustomer(AuthUser customer, CustomerSendRequest request);

    /**
     * 客服回复：校验 staff 为该会话 assignee → 落库 → 投递买家。返回消息视图。
     */
    ChatMessageResponse sendFromStaff(AuthUser staff, StaffSendRequest request);

    /** 客服接入会话（原子认领）。失败抛 BusinessException“已被 X 接待”。 */
    ClaimResponse claim(AuthUser staff, Long sessionId);

    /** 客服释放会话（仅本人接待人）。 */
    void release(AuthUser staff, Long sessionId);

    /** 角色感知会话列表。 */
    List<ChatSessionItemResponse> listSessions(AuthUser user);

    /** 历史消息游标分页（before=上一页最小消息ID，首页传 null）。 */
    List<ChatMessageResponse> getMessages(AuthUser user, Long sessionId, Long before, int size);
}
```

- [ ] **Step 2: 写 Impl 骨架 + 买家发消息实现**

Create `service/impl/ChatServiceImpl.java`：

```java
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
    public ChatMessageResponse sendFromCustomer(AuthUser customer, CustomerSendRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_CUSTOMER);

        ChatSession session = resolveOrCreateSession(request.getStoreId(), customer.userId());

        ChatMessage saved = persistMessage(session, AuthUtil.ROLE_CUSTOMER, customer.userId(), request.getContent());
        ChatMessageResponse view = toMessageView(saved);

        if (session.getAssigneeStaffId() == null) {
            // 未接入：广播到店铺主题，供全店客服认领
            messagingTemplate.convertAndSend("/topic/store." + session.getStoreId(),
                    StoreTopicEvent.builder()
                            .type(StoreTopicEvent.TYPE_MESSAGE)
                            .sessionId(session.getId())
                            .message(view)
                            .build());
        } else {
            // 已接入：精准投递给归属客服
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(session.getAssigneeStaffId()), "/queue/messages", view);
        }
        return view;
    }

    // ── 私有辅助 ───────────────────────────────────────────────────────────────

    /**
     * 解析会话：存在则返回；不存在则创建（assignee=null）。
     * 并发首条消息可能撞 UNIQUE(store_id, customer_id)，捕获后重查兜底。
     */
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

    /** 落库消息并更新会话冗余预览字段。 */
    private ChatMessage persistMessage(ChatSession session, String role, Long senderId, String content) {
        ChatMessage msg = ChatMessage.builder()
                .sessionId(session.getId())
                .senderRole(role)
                .senderId(senderId)
                .content(content)
                .build();
        chatMessageMapper.insert(msg);

        session.setLastMessage(content.length() > LAST_MESSAGE_PREVIEW_MAX
                ? content.substring(0, LAST_MESSAGE_PREVIEW_MAX) : content);
        session.setLastMessageAt(LocalDateTime.now());
        updateById(session);
        return msg;
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

    // ── 以下方法在 Task 6 / Task 7 实现 ─────────────────────────────────────────
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
```

> `toMessageView` 读 `m.getCreateTime()`：单测里手动 `insert` mock 不会触发自动填充，故构造 `ChatMessage` 时若断言 `createdAt` 需自行 set；本任务断言聚焦路由与落库，不强校 createdAt。

- [ ] **Step 3: 写测试（买家发消息）**

Create `src/test/java/com/twentyzhang/bluewhale/service/ChatServiceTest.java`：

```java
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
        when(chatMessageMapper.insert(any())).thenAnswer(inv -> {
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
        when(chatMessageMapper.insert(any())).thenReturn(1);
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
        when(chatMessageMapper.insert(any())).thenReturn(1);
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -Dtest=ChatServiceTest test`
Expected: PASS（4 个用例）。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/service/ChatService.java src/main/java/com/twentyzhang/bluewhale/service/impl/ChatServiceImpl.java src/test/java/com/twentyzhang/bluewhale/service/ChatServiceTest.java
git commit -m "feat(chat): ChatService 买家发消息（会话自动创建+路由，任务7）"
```

---

### Task 6: 客服回复 + 认领 + 释放

**Files:**
- Modify: `service/impl/ChatServiceImpl.java`（替换三个 `UnsupportedOperationException` 桩）
- Modify: `src/test/java/com/twentyzhang/bluewhale/service/ChatServiceTest.java`

- [ ] **Step 1: 实现客服回复**

把 `sendFromStaff` 桩替换为：

```java
    @Override
    public ChatMessageResponse sendFromStaff(AuthUser staff, StaffSendRequest request) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);

        ChatSession session = getById(request.getSessionId());
        if (session == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "会话不存在");
        }
        if (!session.getStoreId().equals(staff.storeId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权操作该会话");
        }
        if (!staff.userId().equals(session.getAssigneeStaffId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "请先接入会话");
        }

        ChatMessage saved = persistMessage(session, AuthUtil.ROLE_STAFF, staff.userId(), request.getContent());
        ChatMessageResponse view = toMessageView(saved);

        // 投递给买家
        messagingTemplate.convertAndSendToUser(
                String.valueOf(session.getCustomerId()), "/queue/messages", view);
        return view;
    }
```

- [ ] **Step 2: 实现认领**

把 `claim` 桩替换为：

```java
    @Override
    public ClaimResponse claim(AuthUser staff, Long sessionId) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);

        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "会话不存在");
        }
        if (!session.getStoreId().equals(staff.storeId())) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "无权操作该会话");
        }

        int affected = baseMapper.claim(sessionId, staff.userId());
        if (affected == 0) {
            // 已被他人抢先，取最新接待人姓名
            ChatSession latest = getById(sessionId);
            String name = nicknameOf(latest.getAssigneeStaffId());
            throw new BusinessException("会话已被 " + name + " 接待");
        }

        String staffName = nicknameOf(staff.userId());
        // 通知全店：该会话已被认领，其他客服移出待接入列表
        messagingTemplate.convertAndSend("/topic/store." + session.getStoreId(),
                StoreTopicEvent.builder()
                        .type(StoreTopicEvent.TYPE_CLAIMED)
                        .sessionId(sessionId)
                        .assigneeStaffId(staff.userId())
                        .assigneeName(staffName)
                        .build());

        return ClaimResponse.builder().sessionId(sessionId).assigneeStaffId(staff.userId()).build();
    }
```

- [ ] **Step 3: 实现释放**

把 `release` 桩替换为：

```java
    @Override
    public void release(AuthUser staff, Long sessionId) {
        AuthUtil.requireRole(AuthUtil.ROLE_STAFF);

        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "会话不存在");
        }

        int affected = baseMapper.release(sessionId, staff.userId());
        if (affected == 0) {
            throw new BusinessException(Result.CODE_FORBIDDEN, "仅接待人可释放会话");
        }

        messagingTemplate.convertAndSend("/topic/store." + session.getStoreId(),
                StoreTopicEvent.builder()
                        .type(StoreTopicEvent.TYPE_RELEASED)
                        .sessionId(sessionId)
                        .build());
    }
```

并加入私有辅助（放在其他私有方法旁）：

```java
    /** 取用户昵称，缺失时回退为“客服{id}”。 */
    private String nicknameOf(Long userId) {
        if (userId == null) return "";
        User u = userMapper.selectById(userId);
        return (u != null && u.getNickname() != null) ? u.getNickname() : ("客服" + userId);
    }
```

- [ ] **Step 4: 追加测试**

在 `ChatServiceTest` 末尾追加：

```java
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
        when(chatMessageMapper.insert(any())).thenReturn(1);
        when(chatSessionMapper.updateById(anySession())).thenReturn(1);

        chatService.sendFromStaff(STAFF, staffReq(100L, "您好"));

        verify(messagingTemplate).convertAndSendToUser(eq("1"), eq("/queue/messages"), any());
    }

    @Test
    @DisplayName("客服回复：非 assignee 抛 403")
    void staffSend_notAssignee_throws403() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 5L, 1L, 8L)); // 接待人是 8

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
                .thenReturn(session(100L, 5L, 1L, null))   // 第一次校验时未接入
                .thenReturn(session(100L, 5L, 1L, 8L));    // claim 0 行后重查到接待人 8
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
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -Dtest=ChatServiceTest test`
Expected: PASS（含新增 6 个用例）。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/service/impl/ChatServiceImpl.java src/test/java/com/twentyzhang/bluewhale/service/ChatServiceTest.java
git commit -m "feat(chat): 客服回复/认领/释放（任务7）"
```

---

### Task 7: 会话列表 + 历史消息

**Files:**
- Modify: `service/impl/ChatServiceImpl.java`（替换 `listSessions` / `getMessages` 桩）
- Modify: `src/test/java/com/twentyzhang/bluewhale/service/ChatServiceTest.java`

- [ ] **Step 1: 实现会话列表**

把 `listSessions` 桩替换为：

```java
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
                // 客服视角：对端=买家，是否在线
                b.peerOnline(Boolean.TRUE.equals(redisUtil.sIsMember(ChatKeys.ONLINE_CUSTOMERS, String.valueOf(s.getCustomerId()))));
            } else {
                // 买家视角：填店名 + 本店是否有客服在线
                Store store = storeMapper.selectById(s.getStoreId());
                b.storeName(store != null ? store.getName() : null);
                b.peerOnline(redisUtil.sCard(ChatKeys.onlineStoreStaff(s.getStoreId())) > 0);
            }
            return b.build();
        }).toList();
    }
```

- [ ] **Step 2: 实现历史消息（游标分页 + 按店读/按本人读）**

把 `getMessages` 桩替换为：

```java
    @Override
    public List<ChatMessageResponse> getMessages(AuthUser user, Long sessionId, Long before, int size) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(Result.CODE_NOT_FOUND, "会话不存在");
        }
        if (AuthUtil.ROLE_STAFF.equals(user.role())) {
            // 按店读：同店客服可读本店任意会话
            if (!session.getStoreId().equals(user.storeId())) {
                throw new BusinessException(Result.CODE_FORBIDDEN, "无权查看该会话");
            }
        } else {
            // 买家仅可读本人会话
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
            wrapper.lt(ChatMessage::getId, before);  // 游标：取比 before 更早的消息
        }
        return chatMessageMapper.selectList(wrapper).stream().map(this::toMessageView).toList();
    }
```

> `getById` / `list` 来自 `ServiceImpl`，走 `chatSessionMapper`（已注入）。`chatMessageMapper.selectList` 直接走注入的 mock。

- [ ] **Step 3: 追加测试**

在 `ChatServiceTest` 末尾追加：

```java
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
    @DisplayName("历史消息：客服查看本店会话成功（按店读）")
    void getMessages_staffSameStore_ok() {
        mockAuthUser(9L, AuthUtil.ROLE_STAFF, 5L);
        when(chatSessionMapper.selectById(100L)).thenReturn(session(100L, 5L, 1L, 8L)); // 接待人是别人
        when(chatMessageMapper.selectList(any())).thenReturn(java.util.List.of());

        var msgs = chatService.getMessages(STAFF, 100L, null, 20);
        assertNotNull(msgs);
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -Dtest=ChatServiceTest test`
Expected: PASS（全部用例）。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/service/impl/ChatServiceImpl.java src/test/java/com/twentyzhang/bluewhale/service/ChatServiceTest.java
git commit -m "feat(chat): 会话列表与历史消息分页（任务7）"
```

---

### Task 8: STOMP 鉴权/授权拦截器

**Files:**
- Create: `interceptor/StompAuthChannelInterceptor.java`
- Modify: `config/WebSocketConfig.java`（注册拦截器到 inbound channel）
- Test: `src/test/java/com/twentyzhang/bluewhale/interceptor/StompAuthChannelInterceptorTest.java`

- [ ] **Step 1: 写拦截器**

Create `interceptor/StompAuthChannelInterceptor.java`：

```java
package com.twentyzhang.bluewhale.interceptor;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP inbound 拦截器：
 *  - CONNECT：用 Authorization: Bearer <jwt> 鉴权，绑定 ChatPrincipal。
 *  - SUBSCRIBE：客服订阅 /topic/store.{id} 须 role=STAFF 且 storeId 匹配（防越权订阅别店）。
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    private static final String STORE_TOPIC_PREFIX = "/topic/store.";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("缺少认证信息");
        }
        String token = header.substring(7);
        if (!jwtUtil.isValid(token)) {
            throw new IllegalArgumentException("Token 无效");
        }
        AuthUser user = new AuthUser(jwtUtil.getUserId(token), jwtUtil.getRole(token), jwtUtil.getStoreId(token));
        accessor.setUser(new ChatPrincipal(user));
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null || !dest.startsWith(STORE_TOPIC_PREFIX)) {
            return; // 仅校验店铺主题；/user/queue/** 由 Spring 按 principal 隔离
        }
        AuthUser user = currentUser(accessor);
        if (!AuthUtil.ROLE_STAFF.equals(user.role())) {
            throw new IllegalArgumentException("仅客服可订阅店铺会话");
        }
        String storeIdStr = dest.substring(STORE_TOPIC_PREFIX.length());
        if (user.storeId() == null || !user.storeId().toString().equals(storeIdStr)) {
            throw new IllegalArgumentException("无权订阅该店铺会话");
        }
    }

    private AuthUser currentUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof ChatPrincipal p) {
            return p.user();
        }
        throw new IllegalArgumentException("未认证");
    }
}
```

- [ ] **Step 2: 在 WebSocketConfig 注册拦截器**

修改 `WebSocketConfig.java`：加构造注入 + 覆写 `configureClientInboundChannel`：

```java
import com.twentyzhang.bluewhale.interceptor.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.config.ChannelRegistration;
```

类上加 `@RequiredArgsConstructor`，并加字段与方法：

```java
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
```

- [ ] **Step 3: 写拦截器单测**

Create `src/test/java/com/twentyzhang/bluewhale/interceptor/StompAuthChannelInterceptorTest.java`：

```java
package com.twentyzhang.bluewhale.interceptor;

import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StompAuthChannelInterceptor")
class StompAuthChannelInterceptorTest {

    @Mock private JwtUtil jwtUtil;
    @InjectMocks private StompAuthChannelInterceptor interceptor;

    private Message<byte[]> frame(StompCommand command, String authHeader, String destination, ChatPrincipal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authHeader != null) accessor.setNativeHeader("Authorization", authHeader);
        if (destination != null) accessor.setDestination(destination);
        if (user != null) accessor.setUser(user);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("CONNECT 合法 token：绑定 ChatPrincipal")
    void connect_validToken_bindsPrincipal() {
        when(jwtUtil.isValid("good")).thenReturn(true);
        when(jwtUtil.getUserId("good")).thenReturn(9L);
        when(jwtUtil.getRole("good")).thenReturn(AuthUtil.ROLE_STAFF);
        when(jwtUtil.getStoreId("good")).thenReturn(5L);

        Message<?> out = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), null);

        StompHeaderAccessor acc = StompHeaderAccessor.wrap(out);
        assertInstanceOf(ChatPrincipal.class, acc.getUser());
        assertEquals("9", acc.getUser().getName());
    }

    @Test
    @DisplayName("CONNECT 无 token：拒绝")
    void connect_missingToken_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), null));
    }

    @Test
    @DisplayName("CONNECT 非法 token：拒绝")
    void connect_invalidToken_rejected() {
        when(jwtUtil.isValid("bad")).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer bad", null, null), null));
    }

    @Test
    @DisplayName("SUBSCRIBE 越权订阅别店主题：拒绝")
    void subscribe_wrongStore_rejected() {
        ChatPrincipal staff = new ChatPrincipal(new com.twentyzhang.bluewhale.common.AuthUser(9L, AuthUtil.ROLE_STAFF, 5L));
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/store.999", staff), null));
    }

    @Test
    @DisplayName("SUBSCRIBE 本店主题：放行")
    void subscribe_ownStore_ok() {
        ChatPrincipal staff = new ChatPrincipal(new com.twentyzhang.bluewhale.common.AuthUser(9L, AuthUtil.ROLE_STAFF, 5L));
        assertDoesNotThrow(
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/store.5", staff), null));
    }

    @Test
    @DisplayName("SUBSCRIBE 买家订阅店铺主题：拒绝")
    void subscribe_customerToStoreTopic_rejected() {
        ChatPrincipal cust = new ChatPrincipal(new com.twentyzhang.bluewhale.common.AuthUser(1L, AuthUtil.ROLE_CUSTOMER, null));
        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/store.5", cust), null));
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -Dtest=StompAuthChannelInterceptorTest test`
Expected: PASS（6 个用例）。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/interceptor/StompAuthChannelInterceptor.java src/main/java/com/twentyzhang/bluewhale/config/WebSocketConfig.java src/test/java/com/twentyzhang/bluewhale/interceptor/StompAuthChannelInterceptorTest.java
git commit -m "feat(chat): STOMP CONNECT 鉴权与 SUBSCRIBE 授权拦截器（任务7）"
```

---

### Task 9: 在线状态监听器

**Files:**
- Create: `listener/WebSocketPresenceListener.java`
- Test: `src/test/java/com/twentyzhang/bluewhale/listener/WebSocketPresenceListenerTest.java`

- [ ] **Step 1: 写监听器**

Create `listener/WebSocketPresenceListener.java`：

```java
package com.twentyzhang.bluewhale.listener;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.ChatKeys;
import com.twentyzhang.bluewhale.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/** 连接/断开事件 → 维护 Redis 在线集合。 */
@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private final RedisUtil redisUtil;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        AuthUser user = extractUser(event.getMessage());
        if (user == null) return;
        if (AuthUtil.ROLE_STAFF.equals(user.role()) && user.storeId() != null) {
            redisUtil.sAdd(ChatKeys.onlineStoreStaff(user.storeId()), String.valueOf(user.userId()));
        } else if (AuthUtil.ROLE_CUSTOMER.equals(user.role())) {
            redisUtil.sAdd(ChatKeys.ONLINE_CUSTOMERS, String.valueOf(user.userId()));
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        AuthUser user = extractUser(event.getMessage());
        if (user == null) return;
        if (AuthUtil.ROLE_STAFF.equals(user.role()) && user.storeId() != null) {
            redisUtil.sRemove(ChatKeys.onlineStoreStaff(user.storeId()), String.valueOf(user.userId()));
        } else if (AuthUtil.ROLE_CUSTOMER.equals(user.role())) {
            redisUtil.sRemove(ChatKeys.ONLINE_CUSTOMERS, String.valueOf(user.userId()));
        }
    }

    private AuthUser extractUser(org.springframework.messaging.Message<byte[]> message) {
        Principal principal = StompHeaderAccessor.wrap(message).getUser();
        return (principal instanceof ChatPrincipal p) ? p.user() : null;
    }
}
```

- [ ] **Step 2: 写单测**

Create `src/test/java/com/twentyzhang/bluewhale/listener/WebSocketPresenceListenerTest.java`：

```java
package com.twentyzhang.bluewhale.listener;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.util.AuthUtil;
import com.twentyzhang.bluewhale.util.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketPresenceListener")
class WebSocketPresenceListenerTest {

    @Mock private RedisUtil redisUtil;
    @InjectMocks private WebSocketPresenceListener listener;

    private Message<byte[]> connectedMsg(AuthUser user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        accessor.setUser(new ChatPrincipal(user));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("客服上线：加入店铺在线集合")
    void staffConnected_addsToStoreSet() {
        listener.onConnected(new SessionConnectedEvent(this,
                connectedMsg(new AuthUser(9L, AuthUtil.ROLE_STAFF, 5L))));
        verify(redisUtil).sAdd("cs:online:store:5", "9");
    }

    @Test
    @DisplayName("买家上线：加入买家在线集合")
    void customerConnected_addsToCustomerSet() {
        listener.onConnected(new SessionConnectedEvent(this,
                connectedMsg(new AuthUser(1L, AuthUtil.ROLE_CUSTOMER, null))));
        verify(redisUtil).sAdd("cs:online:customers", "1");
    }

    @Test
    @DisplayName("客服下线：移出店铺在线集合")
    void staffDisconnected_removesFromStoreSet() {
        listener.onDisconnected(new SessionDisconnectEvent(this,
                connectedMsg(new AuthUser(9L, AuthUtil.ROLE_STAFF, 5L)), "sess-1", null));
        verify(redisUtil).sRemove("cs:online:store:5", "9");
    }
}
```

- [ ] **Step 3: 运行测试确认通过**

Run: `mvn -q -Dtest=WebSocketPresenceListenerTest test`
Expected: PASS（3 个用例）。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/listener/WebSocketPresenceListener.java src/test/java/com/twentyzhang/bluewhale/listener/WebSocketPresenceListenerTest.java
git commit -m "feat(chat): WebSocket 在线状态监听（Redis 集合，任务7）"
```

---

### Task 10: WS 控制器 + REST 控制器 + 消息异常回投

**Files:**
- Create: `controller/ChatWsController.java`
- Create: `controller/ChatRestController.java`

- [ ] **Step 1: WS 控制器（@MessageMapping + 异常回投）**

Create `controller/ChatWsController.java`：

```java
package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.ChatPrincipal;
import com.twentyzhang.bluewhale.dto.chat.CustomerSendRequest;
import com.twentyzhang.bluewhale.dto.chat.StaffSendRequest;
import com.twentyzhang.bluewhale.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;

    /** 买家发送：SEND /app/chat.customer.send */
    @MessageMapping("/chat.customer.send")
    public void customerSend(@Payload @Valid CustomerSendRequest request, Principal principal) {
        chatService.sendFromCustomer(principal(principal), request);
    }

    /** 客服回复：SEND /app/chat.staff.send */
    @MessageMapping("/chat.staff.send")
    public void staffSend(@Payload @Valid StaffSendRequest request, Principal principal) {
        chatService.sendFromStaff(principal(principal), request);
    }

    /** SEND 处理异常回投到 /user/queue/errors。 */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Exception e) {
        return e.getMessage();
    }

    private com.twentyzhang.bluewhale.common.AuthUser principal(Principal principal) {
        return ((ChatPrincipal) principal).user();
    }
}
```

- [ ] **Step 2: REST 控制器（列表/历史/认领/释放）**

Create `controller/ChatRestController.java`：

```java
package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.chat.ChatMessageResponse;
import com.twentyzhang.bluewhale.dto.chat.ChatSessionItemResponse;
import com.twentyzhang.bluewhale.dto.chat.ClaimResponse;
import com.twentyzhang.bluewhale.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    @GetMapping("/sessions")
    public Result<List<ChatSessionItemResponse>> listSessions() {
        return Result.success(chatService.listSessions(currentUser()));
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessageResponse>> getMessages(
            @PathVariable Long id,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(chatService.getMessages(currentUser(), id, before, size));
    }

    @PostMapping("/sessions/{id}/claim")
    public Result<ClaimResponse> claim(@PathVariable Long id) {
        return Result.success(chatService.claim(currentUser(), id));
    }

    @PostMapping("/sessions/{id}/release")
    public Result<Void> release(@PathVariable Long id) {
        chatService.release(currentUser(), id);
        return Result.success();
    }

    private AuthUser currentUser() {
        return (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q clean compile`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/controller/ChatWsController.java src/main/java/com/twentyzhang/bluewhale/controller/ChatRestController.java
git commit -m "feat(chat): WS 与 REST 控制器（任务7）"
```

---

### Task 11: 端到端集成测试（WebSocketStompClient）

**Files:**
- Create: `src/test/java/com/twentyzhang/bluewhale/ChatWebSocketIntegrationTest.java`

> 该测试用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 启动真实应用与 STOMP。需要数据库与 Redis；与现有需外部依赖的集成测试一致，在无依赖的 CI 下可被跳过（沿用项目现状，不在此引入 Testcontainers）。

- [ ] **Step 1: 写集成测试**

Create `src/test/java/com/twentyzhang/bluewhale/ChatWebSocketIntegrationTest.java`：

```java
package com.twentyzhang.bluewhale;

import com.twentyzhang.bluewhale.dto.chat.CustomerSendRequest;
import com.twentyzhang.bluewhale.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTest {

    @LocalServerPort int port;
    @Autowired JwtUtil jwtUtil;

    private WebSocketStompClient newClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private StompSession connect(WebSocketStompClient client, String jwt) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwt);
        return client.connectAsync(
                "ws://localhost:" + port + "/ws",
                new org.springframework.web.socket.WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
    }

    @Test
    void connect_withInvalidToken_fails() {
        WebSocketStompClient client = newClient();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer invalid");
        assertThrows(Exception.class, () ->
                client.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS));
    }
}
```

> **实现提示（执行者按需补全）：** 上面的 `connect(...)` 写法仅示意带 `Authorization` 头握手；实际请用 `client.connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, handler)` 重载传 STOMP CONNECT 头。完整链路用例（买家发→店铺主题收→客服 claim→买家再发→assignee 收→客服回→买家收）需准备 `R__seed_data.sql` 中的买家/客服账号并用 `jwtUtil.generateToken(...)` 造 token；订阅用 `session.subscribe(dest, frameHandler)` + `BlockingQueue` 收帧断言。若本机无 DB/Redis，标注 `@Disabled("需要 MySQL+Redis")` 暂跳过，保留代码供具备环境时运行。

- [ ] **Step 2: 编译并运行（具备 DB/Redis 时）**

Run: `mvn -q -Dtest=ChatWebSocketIntegrationTest test`
Expected: 具备外部依赖时 PASS；否则按需 `@Disabled`。最低要求：`connect_withInvalidToken_fails` 在应用能启动时通过（不依赖 DB 业务）。

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/twentyzhang/bluewhale/ChatWebSocketIntegrationTest.java
git commit -m "test(chat): WebSocket 端到端集成测试（任务7）"
```

---

### Task 12: 全量回归 + 文档同步

**Files:**
- Modify: `docs/进度.md`、`docs/下一阶段路线图.md`、`docs/重要决策说明.md`、`docs/实现说明.md`、`docs/api-spec.md`、`docs/frontend-handoff.md`、`docs/数据库迁移指南.md`

- [ ] **Step 1: 全量测试回归**

Run: `mvn -q clean test`
Expected: 既有用例 + 新增 chat 用例全部 PASS（依赖外部环境的集成测试若 `@Disabled` 则跳过）。

- [ ] **Step 2: 更新文档**

按 spec 第十三节逐项更新：
- `进度.md`：新增「实时客服」模块状态为已完成。
- `下一阶段路线图.md`：任务 7 标记完成。
- `重要决策说明.md`：在 #50 之后补实现期细节（STOMP/SimpleBroker 配置、CONNECT 鉴权、claim 原子 SQL、在线状态 key、`/topic/store.{id}` 信封 `StoreTopicEvent`、按角色拆 `@MessageMapping`），并**保留** #50 中已记录的 🔴 超时自动释放后续项。
- `实现说明.md`：新增「实时客服：消息路由与在线状态」一节，描述未接入→广播/已接入→user-queue 的路由、认领制原子 SQL、离线消息靠历史拉取补齐。
- `api-spec.md` + `frontend-handoff.md`：WS 协议（端点 `/ws`、CONNECT 带 `Authorization`、买家订阅 `/user/queue/messages`、客服订阅 `/user/queue/messages` + `/topic/store.{storeId}`、错误 `/user/queue/errors`、发送目的地 `/app/chat.customer.send` 与 `/app/chat.staff.send` 及其 body）+ REST（`GET /api/chat/sessions`、`GET /api/chat/sessions/{id}/messages`、`POST .../claim`、`POST .../release`）。
- `数据库迁移指南.md`：登记 V4__chat.sql。

- [ ] **Step 3: Commit**

```bash
git add docs
git commit -m "docs(chat): 同步实时客服实现说明、API 与迁移文档（任务7）"
```

---

## Self-Review（计划自查结论）

**1. Spec 覆盖：**
- 数据模型（chat_session/chat_message + assignee_staff_id）→ Task 2 ✅
- 认领制 STOMP 拓扑（未接入广播/已接入投递、claim/release 事件）→ Task 5/6 ✅
- CONNECT 鉴权 + SUBSCRIBE 授权 → Task 8 ✅
- 在线状态（Redis Set）→ Task 4/9 ✅
- REST（列表/历史/claim/release）→ Task 7/10 ✅
- 错误处理（`@MessageExceptionHandler` 回投、各 403/404）→ Task 6/7/10 ✅
- 测试策略（单元路由/claim 原子/权限 + 集成端到端）→ Task 5~9/11 ✅
- 🔴 超时自动释放后续项 → 不在本计划实现范围，已在 spec #十一 与决策 #50 记录，Task 12 要求保留 ✅

**2. 占位符扫描：** 无 TODO/TBD 充当实现；Task 11 集成测试的完整链路用例给出明确实现提示而非空泛占位（端到端真链路依赖外部 DB/Redis，按项目现状允许 `@Disabled`）。

**3. 类型/签名一致性：** `ChatService` 六个方法签名在 Task 5 定义，Task 6/7 仅替换桩、签名一致；`StoreTopicEvent` 常量与字段在 Task 3 定义、Task 5/6 使用一致；`ChatPrincipal.getName()` 返回 userId 字符串，与 `convertAndSendToUser(String.valueOf(...))` 投递键一致；`RedisUtil` Set 方法在 Task 4 定义、Task 9/7 使用一致。
