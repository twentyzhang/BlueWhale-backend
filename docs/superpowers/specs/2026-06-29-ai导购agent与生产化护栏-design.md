# AI 导购 Agent（Function Calling）+ AI 生产化护栏 — 设计文档

> 子项目 AI-3。在 AI-1（语义搜索）/ AI-2（RAG 导购问答）基座上，叠加**能调用真实业务接口的 Agent**（Function Calling），并为整个 AI 接口面补齐**生产化护栏**（限流 / 超时 / 熔断 / 监控）。
>
> 日期：2026-06-29
> 状态：设计已评审通过，待转 plan
> 关联：spec `2026-06-27-rag-design.md`（AI-2，本项目检索/生成基座）、`2026-06-15-ai语义搜索-design.md`（AI-1）

---

## 一、背景与目标

### 现状

- AI-2 RAG 导购问答（`GET /api/products/qa`）已落地：语义检索 → 构造 prompt → qwen 流式生成「推荐话术」。**单轮、纯生成**，LLM 只能基于一次性塞入的候选商品作答，不能主动查任何数据。
- `ChatCompletionClient.streamChat(messages, onDelta)` 抽象目前**不支持工具调用**（无 `tools`/`tool_calls` 概念）。
- 生产化基础设施**全部为零**：pom 中无 Actuator、Micrometer/Prometheus、Resilience4j、限流库。AI 接口直打外部**付费** DashScope，无超时/熔断/限流/可观测性。

### 目标

本子项目交付两块强相关能力：

1. **AI 导购 Agent（Function Calling，方向一 A3）**：让 LLM 能自主决定调用一组业务工具（搜商品、查库存、查我的订单、优惠试算等）获取**真实数据**，再据此作答，从「问答」升级为「能查实情的专属助手」。
2. **AI 生产化护栏（方向三 B2+B3）**：为整个 AI 接口面（assistant / qa / semantic）补齐**限流、超时、熔断、监控**。AI 接口正是最需要这些护栏的地方——外部调用有成本、有延迟、会失败。

> 两块捆绑的理由：护栏有了**具体的消费者**（AI 端点）而非抽象铺设，Agent 也因此具备生产可用性。

### 范围与非目标（YAGNI）

| 范围内 | 非目标（留作后续） |
|---|---|
| Function Calling Agent（手写循环 + 工具注册表） | Spring AI / LangChain4j 等框架（会盖掉现有手写 AI 代码） |
| 7 个**只读 + 个人数据**工具 | **写操作**工具（加购 / 下单）——风险高，本轮不做 |
| **单轮请求**、内部多轮工具循环 | **跨请求多轮会话记忆**（A2，需会话存储 + 上下文裁剪，下一轮） |
| 限流 / 超时 / 熔断 / Prometheus 指标 | Grafana 仪表盘（仅给抓取配置，非交付物）；分布式追踪（B 系列其余项） |
| embedding 现状（名 + 类目） | embedding 文本加固加 `product.description`（A1，下一轮） |

---

## 二、关键决策（评审已确认）

| # | 决策 | 取舍 |
|---|---|---|
| 1 | **手写 Agent 循环 + ToolRegistry**（方案 A），不引框架 | 延续项目「RestClient 直调 + `ChatCompletionClient` 抽象」调性，在现有 AI 代码上**自然生长**而非替换；最透明、最好测、最能体现对 Function Calling 机制的掌握。 |
| 2 | Agent **需登录（CUSTOMER）**，工具含公开只读 + 个人数据 | 个人化体验（查我的订单/券）需身份；写操作不在本轮。 |
| 3 | **单轮请求 / 内部多轮工具循环**，不跨请求记忆 | 聚焦 Function Calling 本身，不引入会话存储与上下文窗口管理复杂度。 |
| 4 | SSE **展示工具步骤**（`step`/`tool` 事件） | 可视化 Agent 推理过程，演示观感最佳、最能体现 Agent 能力。 |
| 5 | 护栏用**成熟库**：Resilience4j + Actuator + Micrometer/Prometheus + Bucket4j | 护栏属基础设施，成熟库是生产级标准姿势、简历加分；与「AI 业务代码零新增依赖」不冲突（护栏是外围）。 |
| 6 | 新端点 `/api/assistant/chat` 与现有 `/api/products/qa` **并存不替换** | qa = 游客「搜 + 推荐话术」；assistant = 登录「能查真实数据的 Agent」，定位不同。 |

---

## 三、架构与组件

### 端点

`GET /api/assistant/chat?q=...`（**需登录 CUSTOMER**，SSE 流式，`text/event-stream`）。

### 组件划分（每个单一职责、可独立测试）

```
AssistantController          REST 入口；捕获 AuthUser → 建 SseEmitter → 交给 service（不阻塞）
        │
AssistantAgentService        Agent 编排：循环调度 LLM 与工具，推 SSE 事件（后台线程池）
   ├── AgentChatClient        扩 ChatCompletionClient：tools-aware 对话，返回「最终文本」或「tool_calls」
   ├── ToolRegistry           按 name 查 Tool；汇总所有工具 JSON schema 供 LLM 选择
   ├── Tool（接口）×7          每个工具薄封装一个已有 Service 调用，含 name/描述/参数 schema/execute
   ├── AgentContext           承载 AuthUser（userId 等），贯穿循环；工具据此鉴权，不读 SecurityContext
   └── AgentProperties        配置：最大轮数 / 每轮超时 / 模型名 / 限流阈值 等
```

### Agent 循环（单次请求内，最多 N 轮，N 默认 5）

1. 构造初始 messages：system（角色 + grounding 约束 + 工具使用规范）+ user（`q`）。
2. 带**全部工具 schema** 调 `AgentChatClient`（**非流式**——需拿到完整 `tool_calls`）。
3. LLM 返回 `tool_calls` → 对每个 tool_call：
   - 发 SSE `step` 事件（「正在做什么」）；
   - `ToolRegistry` 执行工具（传入 `AgentContext`）；
   - 命中商品类工具时额外推 `products` 事件（商品卡）；
   - 工具结果（裁剪后）作为 `tool` 角色消息追加进 messages；
   - 回到第 2 步。
4. LLM 返回**最终文本**（无 tool_calls）→ 切**流式**重发最后一轮 → token 以 `answer` 事件推出 → `done`。
5. 触达最大轮数仍未收敛 → 发 `error`（「这个问题有点复杂，换个问法试试」）并结束，**防止无限循环 / 烧 token**。

### 复用现有资产

- 业务 Service 原样调用：`SemanticSearchService` / `ProductService` / `OrderService` / `CouponService` / `CouponGroupService` / `RecommendationService`（工具只是薄封装）。
- 后台流式：照搬 AI-2 的 `ragStreamExecutor`（线程池）+ `SseEmitter` 主线程秒返回模式。
- LLM 抽象：**扩展** `ChatCompletionClient`（加 tools-aware 方法）/ `ChatMessage`，而非新建一套。
- 鉴权模式：照搬支付回调「鉴权在入口捕获、后台线程不读 SecurityContext」的既有范式。

---

## 四、工具集与鉴权上下文

### `Tool` 接口（每个工具一个 bounded unit）

```java
interface Tool {
    String name();                          // LLM 用的函数名，如 "search_products"
    String description();                   // 给 LLM 看的用途说明（影响它是否选用）
    Map<String,Object> parametersSchema();  // JSON Schema，声明入参
    Object execute(JsonNode args, AgentContext ctx);  // 执行，返回可序列化结果
}
```

### `AgentContext`

REST 入口由 `AuthUser` 构造，贯穿整个 Agent 循环。**工具不读 SecurityContext**（后台线程取不到）；个人数据工具一律用 `ctx.userId()` 过滤，杜绝越权。

### 首批工具清单（7 个）

| 工具 name | 包的 Service 方法 | 类别 | 说明 |
|---|---|---|---|
| `search_products` | `SemanticSearchService.search` | 公开 | 语义搜商品（含分类/价格过滤），返回商品摘要 |
| `get_product_detail` | `ProductService.getProductDetail` | 公开 | 查单品详情（价格/库存/评分） |
| `check_stock` | `ProductService`（库存字段） | 公开 | 查某商品当前库存（「还有货吗」） |
| `list_claimable_coupons` | `CouponGroupService.listCouponGroups` | 公开 | 查可领优惠券 |
| `preview_coupon` | `OrderService.previewCoupon` | 公开 | 优惠试算（「这单能省多少」） |
| `get_my_orders` | `OrderService.getMyOrders` | **个人** | 查我的订单状态/物流，按 `ctx.userId()` 过滤 |
| `list_my_coupons` | `CouponService.getMyCoupons` | **个人** | 查我已领的券，按 `ctx.userId()` 过滤 |

### 设计要点

- **结果裁剪**：工具结果回灌给 LLM 前裁剪/摘要（只留关键字段、限条数，如 search 最多回 top-8），控制 token 与上下文长度。
- **商品卡解耦**：命中 `search_products`/`get_product_detail` 时，按现有 `ProductListItemResponse` 单独推 `products` 事件给前端渲染，与文本回答解耦。
- **可扩展（开闭原则）**：新增能力 = 加一个 `Tool` 实现并注册，Agent 循环与 SSE 协议不变。
- **YAGNI**：首批无任何写操作，与「只读 + 个人数据」边界一致。

---

## 五、SSE 事件协议与数据流

### 事件协议（在 AI-2 的 `products/answer/done/error` 上扩 `step`/`tool`）

| 事件 | 时机 | data | 前端处理 |
|---|---|---|---|
| `step` | 每轮 LLM 决定调工具时 | `{tool, label}`，如 `{"tool":"check_stock","label":"📦 正在查询库存…"}` | 展示「Agent 正在做什么」步骤气泡 |
| `tool` | 工具执行完 | `{tool, ok}`（仅状态，不回灌原始数据给前端） | 把上一步标记完成/失败 |
| `products` | 命中商品类工具时 | 商品摘要数组（`ProductListItemResponse`） | 渲染商品卡（复用现有组件） |
| `answer` | 最终回答轮流式 | 增量文本 token | 打字机追加 |
| `done` | 正常结束 | `""` | 关闭连接 |
| `error` | 异常/超轮数/限流/熔断 | 提示文案 | 展示提示并关闭 |

### 典型数据流（「我想买降噪耳机，我有能用的券吗」）

```
1. user q → system + user messages
2. LLM → tool_calls[search_products]
      → SSE step + 执行 + SSE products(耳机卡) + SSE tool
3. 回灌结果 → LLM → tool_calls[list_my_coupons(ctx.userId)]
      → SSE step + 执行 + SSE tool
4. 回灌结果 → LLM → 最终文本（无 tool_calls）
      → 切流式 → SSE answer* → SSE done
```

### 错误处理（分层短路，全部以 `error` 事件优雅收口）

- **单个工具执行异常**：不炸整局——错误作为 `tool` 结果（`{error:...}`）回灌，让 LLM 换工具或如实告知；连续失败或致命异常才发 `error` 收尾。
- **LLM 调用失败**：被 §六 的超时/熔断兜住，发 `error`（「助手繁忙，请稍后再试」）。
- **超最大轮数**：发 `error`（「换个问法试试」），防死循环烧 token。
- **空结果**：工具查无 → 正常进入回答轮，LLM 在 grounding 约束下如实回「没找到」，不编造。
- **客户端断开**：`emitter.send` 抛异常即停止后续推送与**剩余轮次**（省 token），与 AI-2 一致。
- **grounding**：system 严格约束「只能依据工具返回的数据回答，不得编造商品/库存/订单」，沿用 RAG grounding 思路。

---

## 六、生产化护栏（B3 限流/超时/熔断 + B2 监控）

### 新增依赖

`resilience4j-spring-boot3`、`spring-boot-starter-actuator`、`micrometer-registry-prometheus`、`bucket4j-core`。

### ① 限流（Bucket4j + Redis，按用户）

- 作用面：AI 端点 `/assistant/chat`、`/products/qa`、`/products/semantic` 统一限流。
- 粒度：登录用户按 `userId`、游客按 IP；令牌桶（默认 20 次/分钟，可配）。
- 落地：桶状态存 Redis（与现有分布式锁同源，多实例一致）；入口用 `HandlerInterceptor`（或轻量 filter）拦截。
- 超限：SSE 端点发 `error`（「请求太频繁，请稍后再试」）/ 非流式返回 429。
- 动机：每次 AI 调用都打外部**付费**接口，限流是真实生产诉求。

### ② 超时 + 熔断 + 重试（Resilience4j）

- 作用面：所有外部 AI 调用——`AgentChatClient`/`QwenChatClient`（qwen）、`TongyiEmbeddingClient`（embedding）、`QdrantProductVectorIndex`（向量库）。
- 策略：`@TimeLimiter`（每轮超时，默认 10s）+ `@CircuitBreaker`（连续失败打开断路、快速失败）+ `@Retry`（瞬时错误重试 1~2 次）。
- **熔断降级**：assistant/qa 在断路打开时发 `error` 友好提示；semantic search **已有的关键词降级**正好作为熔断 fallback，二者串联。

### ③ 监控（Actuator + Micrometer + Prometheus）

- 暴露：`/actuator/health` + `/actuator/prometheus`（后者**仅 ADMIN / 内网**，SecurityConfig 受控放行）。
- 自定义指标：
  - `ai.llm.calls`（计数，tag: endpoint/outcome）、`ai.llm.latency`（计时）
  - `ai.agent.rounds`（每次请求工具轮数分布）
  - `ai.tool.invocations`（tag: tool name/ok）
  - `ai.ratelimit.rejected`（计数）
  - Resilience4j 断路器/超时指标经 Micrometer 自动暴露。
- Grafana 可选（仅文档给 Prometheus 抓取配置，非交付物）。

### 安全注意

actuator 默认仅开 `health`，`prometheus` 走鉴权；`env`/`heapdump` 等敏感端点一律不暴露。

---

## 七、测试策略

延续「纯 Mockito 单测为主 + 端到端集成兜底 + 基础设施不可用 `assumeTrue` 优雅跳过」传统。

### ① 纯单元测试（主力）

- `ToolRegistryTest`：按 name 查找、schema 汇总、未知工具报错。
- 各 `Tool` 实现：参数解析、**个人数据工具按 `ctx.userId()` 过滤**（越权防护重点断言：A 用户查不到 B 的订单）、结果裁剪/摘要。
- `AssistantAgentServiceTest`（mock `AgentChatClient` + `ToolRegistry` + 同步 executor）：
  - 正常多轮：tool_calls → 执行 → 回灌 → 最终文本，断言事件序列 `step→tool→products→answer*→done`。
  - 工具执行失败 → 错误回灌、不炸整局。
  - 超最大轮数 → 发 `error` 短路（验证不无限循环）。
  - 空结果 → 进入回答轮、不调多余工具。
  - 客户端断开 → 停止后续轮次。
- `AgentChatClientTest`：解析 OpenAI 兼容响应的 `tool_calls`（含多工具）/ 纯文本两分支；流式最终轮逐 token。

### ② 护栏单元测试

- `RateLimitInterceptorTest`（mock 桶：超限拒绝、按 userId/IP 分桶）。
- 熔断/超时：用 Resilience4j 测试支持或小集成验证「断路打开 → 走降级」「semantic 熔断 → 关键词降级」。

### ③ 端到端集成测试（`@SpringBootTest`，基础设施不可用 `assumeTrue` 跳过）

- `AssistantChatIntegrationTest`：桩 `AgentChatClient`（脚本化 tool_calls）+ 真实工具链 + 真实 SSE，验证全链路事件流与鉴权（未登录 401）。
- **不打真实 DashScope**（用桩），CI 可跑、不烧 token、不需 key。

### ④ 监控冒烟

- `/actuator/prometheus` 含自定义指标名；非 ADMIN 访问被拒。

### 目标

覆盖 Agent 编排正常/异常/边界、工具越权防护、护栏行为；新增约 30~40 个测试，保持全量绿。

---

## 八、文档义务（实现后同步）

- `docs/进度.md`：新增「模块十六：AI 导购 Agent」详表，标完成。
- `docs/实现说明.md`：Agent 循环 / 工具集 / 护栏的「怎么做」。
- `docs/重要决策说明.md`：新增条目（手写 Agent vs 框架、护栏库选型、actuator 暴露策略等）。
- `docs/api-spec.md` + `docs/frontend-handoff.md`：新增 `GET /api/assistant/chat`（SSE 事件协议、需登录、限流 429/error）。
- `docs/项目说明.md`：概览指标（模块数/接口数/依赖/测试数）、技术栈（Resilience4j/Actuator/Prometheus/Bucket4j）、技术亮点（Agent + 护栏）。
- `CLAUDE.md`：本地依赖表补 actuator/prometheus 说明（若需）。

---

## 九、待实现清单（交 writing-plans 细化）

1. 扩展 `ChatCompletionClient`/`ChatMessage` 支持 tools/tool_calls；实现 `AgentChatClient`（qwen OpenAI 兼容 tools）。
2. `Tool` 接口 + `AgentContext` + `ToolRegistry` + 7 个工具实现。
3. `AssistantAgentService`（Agent 循环 + SSE 事件）+ `AssistantController` + `AgentProperties`。
4. SecurityConfig 放行/鉴权调整（`/assistant/chat` 需登录、`/actuator/prometheus` 仅 ADMIN）。
5. 护栏：Bucket4j+Redis 限流拦截器、Resilience4j 超时/熔断/重试注解与降级、Actuator+Micrometer+Prometheus 指标。
6. 测试：单元 + 护栏 + 端到端桩集成 + 监控冒烟。
7. 文档同步（见 §八）。
