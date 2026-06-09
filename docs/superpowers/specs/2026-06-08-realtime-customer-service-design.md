# 实时客服功能 — 设计定稿

> 状态：**设计已定稿，待用户评审 → 进入 writing-plans**。
> 创建：2026-06-08，定稿：2026-06-09。来源：[下一阶段路线图.md](../../下一阶段路线图.md) 任务 7（客服实时聊天）。
> 关键选型已同步至 [重要决策说明.md](../../重要决策说明.md) 第 50 条。

---

## 一、目标与范围

为 BlueWhale 商城增加**买家 ↔ 店铺客服**的实时聊天。买家就某店铺发起咨询，由该店客服接待。

**v1 范围（做）：** 双向实时收发 + 消息落库 + 离线消息 + 历史拉取 + 在线状态 + 角色感知会话列表 + **会话认领/释放（独占接待）**。

**v1 明确不做（YAGNI）：** 未读数统计、已读回执、工单状态机/排队、文件/图片消息、输入中状态、客服机器人、多实例水平扩展、自动路由派单。

---

## 二、已锁定决策

| # | 决策点 | 选择 |
|---|---|---|
| 1 | 会话主体 | 买家 ↔ 店铺客服；会话挂在 `storeId` 下 |
| 2 | **会话归属** | **认领制（claim/pull）+ 独占接待 + 释放**（详见决策说明 #50，已否决哈希分配/共享收件箱/自动路由） |
| 3 | 会话粒度 | 方案 A 可复用会话：同一买家×店铺永远一个会话，`UNIQUE(store_id, customer_id)`，无状态机，首条消息时自动创建 |
| 4 | 传输方案 | STOMP over WebSocket（`spring-boot-starter-websocket` + `@EnableWebSocketMessageBroker`） |
| 5 | WS 鉴权 | 复用现有 JWT，在 STOMP `CONNECT` 帧带 `Authorization: Bearer <jwt>`，`ChannelInterceptor` 校验后绑定 `AuthUser` 为 Principal |
| 6 | 接待客服掉线 | 方案 A 粘性：`assignee_staff_id` 不变，掉线期间买家消息成为离线消息，重连后历史补齐 |
| 7 | 客服读写权限 | **按店读、按归属写**：同店任意客服可读本店任何会话历史；仅 assignee 可发消息 |
| 8 | 部署规模 | 单实例 + 内存 SimpleBroker（零外部 broker 依赖）；在线状态用 Redis |

---

## 三、现有可复用基础设施

- **鉴权**：JWT 无状态，token 内含 `userId / role(CUSTOMER/STAFF/ADMIN) / storeId`；`AuthUser` record 存于 SecurityContext。WebSocket 握手不走普通请求头，故在 STOMP CONNECT 帧鉴权。
- **多商户**：每店有 Staff，已有 `AuthUtil.requireStoreAccess` 等店铺权限工具。
- **Redis**：已接入（`RedisUtil` / `RedisConfig` StringRedisTemplate），用于在线状态。
- **实体范式**：MyBatis Plus + `@TableLogic deleted` + `created_at` 自动填充（`MyMetaObjectHandler`）。
- **迁移**：Flyway，下一个版本号 **V4**（现有到 V3）。

---

## 四、数据模型（迁移 `V4__chat.sql`）

```
chat_session  会话（一个买家 ↔ 一个店铺）
├─ id                BIGINT PK
├─ store_id          BIGINT       归属店铺
├─ customer_id       BIGINT       买家
├─ assignee_staff_id BIGINT NULL  接待客服（null=未接入）  ★ 认领制核心
├─ last_message_at   DATETIME     会话列表排序（冗余）
├─ last_message      VARCHAR(120) 最后一条预览（冗余，避免列表 N+1）
├─ created_at        DATETIME
├─ updated_at        DATETIME
├─ deleted           TINYINT
└─ UNIQUE KEY (store_id, customer_id)   ★ 一买家×一店铺=一会话

chat_message  消息
├─ id           BIGINT PK
├─ session_id   BIGINT        所属会话
├─ sender_role  VARCHAR(16)   CUSTOMER / STAFF
├─ sender_id    BIGINT        发送者 userId
├─ content      VARCHAR(1000)
├─ created_at   DATETIME
├─ deleted      TINYINT
└─ INDEX (session_id, id)     历史分页游标
```

会话在买家首条消息时自动创建（先查后插 / `ON DUPLICATE KEY`），`assignee_staff_id` 初始为 null。

---

## 五、STOMP 消息拓扑（认领制）

WS 端点 `/ws`（纯 WebSocket，前端独立项目，暂不加 SockJS 回退）。Broker：内存 SimpleBroker，主题前缀 `/topic`、私信前缀 `/queue`，应用前缀 `/app`，user 前缀 `/user`。

**鉴权/授权拦截器 `StompAuthChannelInterceptor`（注册在 inbound channel）：**
- `CONNECT`：读 header `Authorization: Bearer <jwt>` → `JwtUtil.isValid` → 构造 `AuthUser` 设为 `accessor.setUser(principal)`；非法抛异常拒连（ERROR 帧 + 断开）。
- `SUBSCRIBE`：校验目的地与身份匹配（客服订阅 `/topic/store.{storeId}` 必须 `role=STAFF` 且 token `storeId` 匹配；防越权订阅别店）。
- `SEND`：校验发送者对 session 的权限（见下）。

**消息流（按会话 `assignee_staff_id` 分两态）：**

```
买家发消息  SEND /app/chat.send {content}     （session 由 storeId+customerId 解析/创建）
   │  落库，看 assignee_staff_id：
   ├─ null（未接入） → 广播 /topic/store.{storeId}   ← 全店客服的“新会话池”
   └─ 已接入        → convertAndSendToUser(归属客服, /queue/messages)   ← 只给接待人

客服接入  POST /api/chat/sessions/{id}/claim   （REST，原子写库 + 返回结果）
   │  UPDATE chat_session SET assignee_staff_id=me WHERE id=? AND assignee_staff_id IS NULL
   ├─ 影响 1 行 → 成功，广播 session.claimed 到 /topic/store.{storeId}（其他客服移出待接入列表）
   └─ 影响 0 行 → 已被抢先，返回业务错误“已被 X 接待”

客服回复  SEND /app/chat.send {sessionId, content}
   │  校验 sender==该 session.assignee（否则拒绝，回 /user/queue/errors）
   └─ 落库 → convertAndSendToUser(买家, /queue/messages)

客服释放  POST /api/chat/sessions/{id}/release  （REST，仅 assignee=me）
   └─ assignee 置 null，广播 session.released 到 /topic/store.{storeId}（重回待接入池）

订阅：
  买家：/user/queue/messages          （Spring 按 principal 自动隔离）
  客服：/user/queue/messages          （收自己接待会话的买家消息）
      + /topic/store.{storeId}        （收 新会话/claimed/released 事件）
  双方：/user/queue/errors            （SEND 失败回投）
```

**为何接入用 REST 而非 STOMP 消息**：claim 需要原子写库（`WHERE assignee IS NULL`）并向调用方返回成功/失败结果，请求-响应语义更合适；产生的实时通知再走 STOMP 广播。原子条件 UPDATE 是防两个客服并发抢同一会话的关键守卫（影响 0 行即已被占）。

---

## 六、在线状态（Redis）

监听 Spring `SessionConnectedEvent` / `SessionDisconnectEvent`（`WebSocketPresenceListener`）：
- 买家上线 `SADD cs:online:customers {userId}`，下线 `SREM`。
- 客服上线 `SADD cs:online:store:{storeId} {userId}`，下线 `SREM`。

查询：
- 买家视角「本店有无客服在线」：`SCARD cs:online:store:{storeId} > 0`。
- 客服视角「某买家是否在线」：`SISMEMBER cs:online:customers {customerId}`。

在线信息内联进会话列表响应，不单开接口。

**接待客服掉线**（决策 #6 粘性）：`assignee_staff_id` 保持不变，掉线期间买家消息照常落库成为离线消息，客服重连后经历史拉取补齐。

**已知限制**：进程异常退出可能漏发 DISCONNECT，留下少量在线脏数据，v1 可接受（后续用心跳 TTL 兜底）。

---

## 七、REST API

```
GET  /api/chat/sessions                         角色感知会话列表
  ├─ Customer：自己的会话（每店一个），含 店名/最后消息/时间/对端是否在线/接待客服名
  └─ Staff：本店会话，按 last_message_at 倒序，分三组：
            · 待接入(assignee=null)  · 我接待的(assignee=me)  · 已被他人接待(assignee=其他,只读)

GET  /api/chat/sessions/{id}/messages?before={msgId}&size=20
        历史游标分页（按 (session_id,id) 倒序）
        权限：买家仅本人会话；客服可读本店任意会话（按店读，决策 #7）

POST /api/chat/sessions/{id}/claim              接入（原子 UPDATE ... WHERE assignee IS NULL）
POST /api/chat/sessions/{id}/release            释放（仅 assignee=me）

WS   /ws  (STOMP)        SecurityConfig 放行 /ws/**，真正鉴权在 CONNECT 帧
```

会话自动创建：无独立“开启会话”接口，买家首条消息时建。

---

## 八、错误处理

| 场景 | 处理 |
|---|---|
| CONNECT 鉴权失败（无/错 JWT） | 拒绝连接，ERROR 帧 + 关闭 |
| 客服回复**未接入**或**非自己接待**的会话 | 拒收，经 `/user/queue/errors` 回错误（须先 claim） |
| SEND 到不属于自己的 session / 不存在的 store | 丢弃 + 错误回投（`@MessageExceptionHandler` 统一兜底） |
| SUBSCRIBE 越权目的地（订阅别店 `/topic/store.*`） | 拦截器拒绝订阅 |
| claim 已被占用 | REST 返回业务错误“已被 X 接待”（影响 0 行） |
| release 非本人 assignee | `BusinessException` 403 |
| 内容校验 | 非空、长度 ≤1000，超限回错误 |
| REST 越权（查别店/别人会话） | `BusinessException` 403/404，走 `GlobalExceptionHandler` |

---

## 九、测试策略

- **单元（纯 Mockito，遵决策 #0）**：会话解析/自动创建；路由决策（未接入→店铺主题 / 已接入→assignee 私信）；claim 原子性（并发仅 1 成功）；release；读=按店、写=按归属的权限校验；内容校验。
- **集成（`WebSocketStompClient` + JWT）**：完整链路——买家发→店铺主题收到→客服 claim→买家再发→仅 assignee 收到→客服回→买家收到；历史游标分页；在线状态（Redis，嵌入或测试容器）；鉴权拒绝（无/错 token）；越权订阅被拒；两客服并发 claim 仅一人成功。
- 复用现有测试基建风格（参考 `OrderServiceTest` 等）。

---

## 十、新增组件清单（最终以实现计划为准）

- `config/WebSocketConfig`（`@EnableWebSocketMessageBroker`，注册 `/ws`、SimpleBroker `/topic` `/queue`、前缀 `/app` `/user`）
- `interceptor/StompAuthChannelInterceptor`（CONNECT 鉴权 + SUB/SEND 授权）
- `listener/WebSocketPresenceListener`（连接/断开 → Redis 在线集合）
- `entity/ChatSession`、`entity/ChatMessage` + `mapper/ChatSessionMapper`、`mapper/ChatMessageMapper`
- `controller/ChatRestController`（会话列表 / 历史 / claim / release）+ `controller/ChatWsController`（`@MessageMapping`）
- `service/ChatService` + `service/impl/ChatServiceImpl`
- `dto/chat/*`（发送请求、消息视图、会话列表项、claim/release 响应、事件 payload）
- `resources/db/migration/V4__chat.sql`
- `SecurityConfig` 放行 `/ws/**`
- `pom.xml` 增 `spring-boot-starter-websocket`

---

## 十一、🔴 v1 之后必做项（用户明确要求记录）

1. **接待会话超时自动释放**：客服接入后若长时间无响应（如 5 分钟），自动将 `assignee_staff_id` 置回 null 退回店铺池，避免买家长期挂在掉线/失联客服名下无人理。当前 v1 仅靠客服**手动 release**，掉线为粘性——**后续一定要改为超时自动释放**（可配合在线状态/心跳判定）。

## 十二、后续可扩展（非必做）

- 多实例水平扩展：SimpleBroker 换 Redis Pub/Sub 中继或外部 broker（RabbitMQ STOMP）。
- 认领制升级为自动路由：把“人点接入”换成路由引擎按在线+负载自动派单，数据模型不变。
- 在线脏数据：心跳 TTL 兜底。
- 未读数、已读回执、输入中状态、文件/图片消息。

---

## 十三、实现后文档义务

`进度.md`（新模块状态）、`下一阶段路线图.md`（任务 7 标完成）、`重要决策说明.md`（#50 已记选型，实现后补 STOMP/SimpleBroker 配置细节、WS 鉴权、claim 原子性等）、`实现说明.md`（消息路由与在线状态流程）、`api-spec.md` + `frontend-handoff.md`（WS 协议与 REST 接口）、`数据库迁移指南.md`（V4）。
