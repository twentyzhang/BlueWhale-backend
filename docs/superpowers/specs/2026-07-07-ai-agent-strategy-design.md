# AI-4 综合型导购 Agent 策略层 - 设计文档

> 日期：2026-07-07  
> 状态：设计草案已讨论通过，待用户复核后进入 plan  
> 关联：AI-1 语义搜索、AI-2 RAG 导购问答、AI-3 AI 导购 Agent + 生产化护栏

---

## 一、背景与目标

### 当前状态

BlueWhale 已完成三条 AI 链路：

- AI-1：`GET /api/products/semantic`，基于 Qdrant + 通义 embedding 的语义商品搜索，失败降级关键词搜索。
- AI-2：`GET /api/products/qa`，基于语义搜索的单轮 RAG 导购问答，SSE 流式输出。
- AI-3：`GET /api/assistant/chat`，登录后可用的多轮工具调用 Agent，具备 6 个只读工具、SSE 事件流、限流、熔断与 Prometheus 指标。

现有 Agent 的骨架已经可用，但“智能度”主要依赖 LLM 自己理解 system prompt 和工具描述。它有几个明显短板：

- 用户需求过于模糊时，Agent 可能直接搜索而不是先追问。
- 订单、优惠券、库存、商品推荐等场景没有显式意图分流，行为稳定性依赖模型自觉。
- system prompt 是一段固定文本，不能按不同意图给模型更具体的工具使用提示。
- 缺少专门验证“追问 / 工具选择 / 综合推荐”的行为测试。

### 目标

本期目标是增强 `/api/assistant/chat` 的综合型导购能力，让它更像一个会判断场景、会追问、会组合工具的导购助手。

交付目标：

1. 新增轻量 Agent 策略层，在进入 LLM tools 循环前判断用户意图。
2. 对明显模糊的问题先追问一个关键问题，不调用 LLM、不调用工具。
3. 对商品推荐、订单、优惠券、库存/详情等场景注入不同 prompt hint，提升工具选择稳定性。
4. 增强 system prompt 和工具描述，明确“最多推荐 3 个、只基于工具结果、不编造”。
5. 补行为测试，覆盖追问、推荐、订单、优惠券、工具失败、超轮数等场景。

---

## 二、范围与非目标

### 本期范围

| 范围 | 说明 |
| --- | --- |
| 继续使用 `/api/assistant/chat` | 不新增接口，不改变登录要求 |
| 轻量策略层 | 规则分类 + 追问策略 + prompt 组合，不新增 AI 框架 |
| 只读 Agent | 工具仍保持只读：搜索商品、查详情、查库存、查可领券、查我的订单、查我的券 |
| SSE 协议兼容 | 沿用 `step/tool/products/answer/done/error`，追问也用 `answer/done` |
| 可测试行为 | 策略层纯 Java，可用单测稳定验证 |

### 非目标

| 非目标 | 原因 |
| --- | --- |
| 长期会话记忆 / 用户画像持久化 | 需要新增存储、上下文裁剪与隐私边界，留给后续 AI-5 |
| 自动下单、自动领券、自动支付 | 涉及写操作与风险控制，本期保持只读 |
| 引入 Spring AI / LangChain4j | 现有手写 Agent 循环足够透明，继续保持学习价值和可控性 |
| 改造 RAG `/api/products/qa` 主流程 | 本期聚焦登录态综合型 Agent |
| 新增前端事件协议 | 追问可用现有 `answer/done` 表达，避免前端适配成本 |

---

## 三、关键决策

| # | 决策 | 原因 |
| --- | --- | --- |
| 1 | 采用“轻量策略层 + 现有 LLM tools 循环”，不重写 Agent 架构 | 既能提升行为稳定性，又不破坏 AI-3 已验证的工具循环与生产化护栏 |
| 2 | 意图分类第一版用规则，不调用 LLM | 成本低、可测试、无 token 消耗；分类只做粗分流，不追求语义完美 |
| 3 | 追问只处理明显模糊问题，并且只问一个问题 | 避免把导购做成问卷；用户给出对象、用途、预算、品类任一信息时直接进入工具链 |
| 4 | 不新增 `clarification` SSE 事件 | 现有前端可把追问当作普通回答展示；协议不变就是低风险 |
| 5 | prompt 按 intent 组合 | 不同场景需要不同工具优先级，动态 hint 比单一 system prompt 稳定 |
| 6 | 本期不做长期记忆 | 综合型第一版先做好单请求内的判断和工具组合，避免范围膨胀 |

---

## 四、架构与组件

### 新增组件

#### `AgentIntent`

枚举，表达粗粒度用户意图：

- `PRODUCT_RECOMMENDATION`：商品推荐、场景导购、预算/用途/对象类问题。
- `PERSONAL_ORDER`：我的订单、物流、上次买了什么。
- `PERSONAL_COUPON`：我的券、能不能用券、有什么优惠。
- `STOCK_OR_DETAIL`：某商品库存、详情、价格。
- `GENERAL_GUIDANCE`：泛导购或不确定，但不明显需要追问。
- `UNCLEAR`：信息过少，应该先追问。

#### `AgentIntentClassifier`

纯 Java 规则分类器。输入用户问题 `q`，输出 `AgentIntent`。

第一版规则：

- 包含“我的订单 / 物流 / 到哪 / 买过 / 上次买” → `PERSONAL_ORDER`
- 包含“我的券 / 优惠券 / 券 / 优惠 / 能用吗” → `PERSONAL_COUPON`
- 包含“库存 / 有货 / 还有吗 / 价格 / 详情” → `STOCK_OR_DETAIL`
- 包含“推荐 / 送 / 适合 / 预算 / 想买 / 夏天 / 长辈 / 礼物 / 无糖”等 → `PRODUCT_RECOMMENDATION`
- “推荐点东西 / 买什么好 / 有什么推荐”且没有对象、用途、预算、品类 → `UNCLEAR`
- 其他 → `GENERAL_GUIDANCE`

#### `AgentClarificationPolicy`

判断是否先追问。只处理明显模糊的问题：

- “推荐点东西”
- “买什么好”
- “有什么推荐”
- “想买礼物”

如果问题中已经包含对象、用途、预算、品类、偏好任一线索，则不追问。

追问文案示例：

> 你是想自己吃/用，还是送人？如果送人，大概预算是多少？

#### `AgentPromptComposer`

负责组合最终 system prompt：

```text
基础角色约束
+ grounding 约束
+ 综合型导购回答格式
+ intent-specific hint
```

意图 hint 示例：

- `PRODUCT_RECOMMENDATION`：优先搜索商品；必要时查详情/库存/券；最多推荐 3 个；按“结论 + 理由 + 价格/库存/优惠 + 下一步建议”回答。
- `PERSONAL_ORDER`：优先调用 `get_my_orders`；不要猜测订单状态；没有数据就如实说明。
- `PERSONAL_COUPON`：优先调用 `list_my_coupons`；必要时再调用 `list_claimable_coupons`。
- `STOCK_OR_DETAIL`：需要具体商品时优先搜索或查详情，再查库存。
- `GENERAL_GUIDANCE`：可以先搜索商品；信息不足但不极端模糊时给出保守建议并提示用户补充条件。

### 改造现有组件

`AssistantAgentServiceImpl.runLoop` 开头增加策略层：

1. `intent = classifier.classify(q)`
2. `clarification = clarificationPolicy.maybeAsk(q, intent)`
3. 如果需要追问：发送 `answer` + `done`，记录轮数为 0 或 1，不调用 LLM 和工具。
4. 如果不需要追问：`systemPrompt = promptComposer.compose(intent)`，进入现有 LLM tools 循环。

`AgentProperties.systemPrompt` 保留为基础 prompt 配置，但由 `AgentPromptComposer` 读取并扩展，不再在 service 中直接使用。

---

## 五、数据流与 SSE

### 模糊需求追问流

用户：

> 推荐点东西

流程：

1. `AgentIntentClassifier` 输出 `UNCLEAR`
2. `AgentClarificationPolicy` 返回追问文案
3. 后端推 SSE：
   - `answer`: 追问文本
   - `done`: `""`
4. 不调用 `AgentChatClient`
5. 不调用任何工具

前端无需改造，按普通回答展示。

### 明确推荐流

用户：

> 送长辈的健康礼物，预算 100

流程：

1. 分类为 `PRODUCT_RECOMMENDATION`
2. 不追问
3. `AgentPromptComposer` 注入推荐场景 hint
4. 进入现有 LLM tools 循环
5. 典型事件：
   - `step`: 正在搜索商品
   - `tool`: `search_products` ok
   - `products`: 商品卡数组
   - `answer`: 流式推荐理由
   - `done`

### 个人信息流

用户：

> 我的优惠券有哪些能用？

流程：

1. 分类为 `PERSONAL_COUPON`
2. prompt hint 要求优先调用 `list_my_coupons`
3. 工具仍使用 `AgentContext.userId()`，LLM 无法越权指定他人用户
4. 最终回答基于工具结果总结

### SSE 兼容性

本期不新增事件。

| 场景 | 事件 |
| --- | --- |
| 追问 | `answer` → `done` |
| 正常工具流 | `step` → `tool` → `products` → `answer` → `done` |
| 异常 | `error` |

---

## 六、Prompt 与工具描述增强

### 基础 prompt 目标

基础 prompt 需要明确：

- 你是南鲸商城综合型导购助手。
- 只能基于工具返回的真实数据回答，不得编造商品、库存、订单、优惠券。
- 信息不足时先追问一个关键问题。
- 推荐最多 3 个商品。
- 回答要结论先行，解释差异，最后给下一步建议。
- 涉及个人订单/优惠券时必须调用个人工具。
- 工具结果为空或失败时如实说明。

### 工具描述增强方向

现有 6 个工具保留，增强 description，使模型更容易选对：

- `search_products`：用于自然语言商品搜索、场景推荐、预算/用途类问题。
- `get_product_detail`：用户提到具体商品、需要价格/评分/详情时使用。
- `check_stock`：用户问“还有货吗 / 库存 / 能不能买”时使用，需要 productId。
- `list_claimable_coupons`：用户问平台/店铺当前有什么可领券时使用。
- `get_my_orders`：用户问“我的订单 / 物流 / 上次买过什么”时使用，只能查当前用户。
- `list_my_coupons`：用户问“我的券 / 我有哪些优惠 / 能不能用券”时使用，只能查当前用户。

---

## 七、错误处理

| 场景 | 处理 |
| --- | --- |
| 策略层分类不准 | 进入 `GENERAL_GUIDANCE`，交给 LLM tools 循环兜底 |
| 追问策略误触发 | 仅影响本次回答，不产生写操作；测试覆盖常见明确需求避免误触发 |
| 工具执行失败 | 维持 AI-3 行为：回灌 `{error:...}` 给 LLM，并发送 `tool ok=false` |
| LLM 失败 / 熔断 | 维持现有 `error` 事件 |
| 超过最大轮数 | 维持现有超轮数 `error` |
| 客户端断开 | 维持现有 `AtomicBoolean` 短路 |

---

## 八、测试策略

### 策略层单测

新增：

- `AgentIntentClassifierTest`
- `AgentClarificationPolicyTest`
- `AgentPromptComposerTest`

覆盖样例：

| 用户问题 | 期望 |
| --- | --- |
| 推荐点东西 | `UNCLEAR`，需要追问 |
| 送长辈的健康礼物，预算 100 | `PRODUCT_RECOMMENDATION`，不追问 |
| 我的订单到哪了 | `PERSONAL_ORDER` |
| 我有哪些券能用 | `PERSONAL_COUPON` |
| 这个商品还有货吗 | `STOCK_OR_DETAIL` |
| 夏天喝点无糖的 | `PRODUCT_RECOMMENDATION` |

### Agent Service 单测

增强 `AssistantAgentServiceTest`：

- 追问场景不调用 `AgentChatClient`
- 追问场景不调用任何工具
- 追问场景只推 `answer` + `done`
- 推荐场景进入原有 LLM tools 循环
- 订单 / 优惠券场景的 system prompt 带对应 intent hint
- 工具失败仍回灌错误 JSON，现有错误收口不变
- 超轮数行为不变

### 集成测试

增强或新增 `AssistantChatIntegrationTest`：

- “推荐点东西” → SSE 含追问文本，且不出现 `step/tool/products`
- “送长辈的健康礼物” → 能看到 `step/products/answer/done`
- “我的优惠券” → 走个人工具路径，仍强制使用当前登录用户上下文

---

## 九、验收标准

完成后应满足：

- 模糊需求会追问，而不是硬搜。
- 明确推荐会走商品工具链。
- 订单 / 券类问题优先走个人工具。
- prompt 明确约束“最多推荐 3 个、必须基于工具结果、不编造”。
- 现有 SSE 协议兼容，不要求前端改造。
- 相关单测与集成测试通过；最终全量测试保持通过。

---

## 十、文档义务

实现后同步：

- `docs/进度.md`：新增 AI-4 状态。
- `docs/实现说明.md`：记录策略层、追问逻辑、prompt composer、SSE 兼容性。
- `docs/重要决策说明.md`：记录为什么选择轻量策略层，而不是长期记忆或框架。
- `docs/下一阶段路线图.md`：AI 优化主线更新。
- `docs/api-spec.md`：说明 `/api/assistant/chat` 行为增强，SSE 事件不变。
- `docs/frontend-handoff.md`：说明追问也走 `answer/done`，不需要新增事件处理。
- `docs/项目说明.md`：如测试数、亮点或后续规划发生变化，同步概览。

---

## 十一、待计划拆分

交 writing-plans 细化：

1. 新增 `AgentIntent`、`AgentIntentClassifier`、`AgentClarificationPolicy`、`AgentPromptComposer`。
2. 改造 `AssistantAgentServiceImpl` 接入策略层。
3. 增强 `AgentProperties.systemPrompt` 默认文案与 6 个工具 description。
4. 增加策略层单测。
5. 增强 Agent service 单测与集成测试。
6. 同步文档并运行验证。
