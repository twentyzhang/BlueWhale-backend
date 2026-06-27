# AI 导购问答（RAG）— 设计文档

> 创建于 2026-06-27。本文档是「AI 应用落地」系列的**第二个**子项目（AI-2）设计。
> 复用 AI-1（语义商品搜索，spec：`2026-06-15-ai语义搜索-design.md`）建立的「通义 embedding + Qdrant 向量库」检索基座，在其上叠加 LLM 生成。

## 目标

在 AI-1 语义检索基座上叠加 LLM 生成，做一个**单轮、流式的商品导购问答**：用户用自然语言提问，系统检索相关商品并由**通义千问（qwen）**生成带推荐理由的回答，同时把检索到的商品作为结构化卡片返回。核心工程学习点是 **RAG 的「检索 → 增强 → 生成」闭环**与 **SSE 流式输出**。

## 定位与范围

- **定位**：学习/作品展示 AI 应用范式（RAG 检索增强生成、严格 grounding、流式输出），先打通最小可演示链路。
- **本期范围（IN）**：
  - 新增 1 个 SSE 接口 `GET /api/products/qa?q=...`（**开放，无需登录**，与语义搜索一致）。
  - 检索**复用 AI-1**（`SemanticSearchService` → 通义 embedding + Qdrant），取 top-K 商品作上下文。
  - 通义千问（`qwen-plus`，DashScope OpenAI 兼容端点，`stream=true`）流式生成；可插拔 `ChatCompletionClient` 抽象。
  - **严格 grounding**：系统提示约束 LLM 只能从检索到的商品里推荐，不许编造目录外商品；检索为空时不调 LLM，短路返回提示语（省 token）。
  - 复用现有 `ProductListItemResponse` 作商品卡结构（一份检索数据两用：既是商品卡又是 LLM 上下文）。
- **明确不做（OUT，留作后续）**：
  - 多轮对话 / 对话历史 / 问答历史持久化。
  - 混合检索（向量+关键词）/ rerank。
  - 并发限流 / 配额 / 计费。
  - RAG 层不再额外做关键词降级（`SemanticSearchService` 内部已有降级；检索仍异常则报错）。

## 关键技术决策

| # | 决策 | 取舍 |
|---|---|---|
| 1 | **商品导购问答**（非 FAQ/知识库，非混合） | 复用现成 `products` 向量库，零新增语料，最快出可演示成果 |
| 2 | **通义千问 `qwen-plus`（DashScope OpenAI 兼容）做生成** | 复用已验证可用且有额度的百炼 key，**与 embedding 同一个账号/key**（`DASHSCOPE_API_KEY`），联调零额外成本。原计划的 DeepSeek 因账户余额不足改用 qwen；二者均 OpenAI 兼容，靠 `ChatCompletionClient` 抽象 + 配置可随时切换 |
| 3 | **单轮问答**（非多轮对话） | YAGNI；先打通「检索增强生成」核心环路，无需会话状态/历史 |
| 4 | **SSE 流式输出**（非一次性 JSON） | 打字机效果，作品展示价值高；也是 AI 应用典型能力 |
| 5 | **方案 A：`SseEmitter` + `RestClient` 读 LLM 流** | 贴合现有 servlet MVC + RestClient 栈，**零新增 Maven 依赖**；代价是手写 SSE chunk 解析（否决 WebFlux 引入响应式栈、否决「假流式」失去意义） |
| 6 | **输出 = 商品卡 + 流式回答** | 导购体验完整：先推 `products` 事件渲染卡片，再流式推 `answer` token |
| 7 | **严格 grounding + 空结果短路** | 只喂检索结果、system 约束「不得编造」，防幻觉推荐不存在的商品；空结果不烧 LLM token |
| 8 | **`ChatCompletionClient` 接口抽象** | 可插拔、可测；呼应既有 `EmbeddingClient`/`PaymentGateway` 套路。OpenAI 兼容，换 DeepSeek/其它仅改 `base-url`/`model`/`key` 配置 |
| 9 | **检索复用 `SemanticSearchService`** | 不重写检索；一份 `List<ProductListItemResponse>` 既当商品卡又当 LLM 上下文 |
| 10 | **专用 `ragStreamExecutor` 跑后台流** | `SseEmitter` 主线程秒返回、流在后台推，不阻塞容器请求线程 |

## 架构与组件

整体形态：**RAG = 检索（复用 AI-1）+ 增强（构造 prompt）+ 生成（qwen 流式）**，控制器用 `SseEmitter` 把生成结果边收边推。

| 组件 | 职责 | 依赖 |
|---|---|---|
| `controller/RagController` | `GET /api/products/qa`，返回 `SseEmitter`（`text/event-stream`），编排交给 service | `RagService` |
| `service/RagService`（+impl） | 编排：检索 → 空结果短路 → 构造 prompt → 流式生成 → 按协议推 SSE 事件 | 下列三者 |
| `service/ChatCompletionClient`（接口）+ `impl/QwenChatClient` | 调 qwen DashScope 兼容端点 `/chat/completions`（`stream=true`），解析 OpenAI SSE chunk，把 delta 文本经回调逐段吐出 | Spring `RestClient`（流式读 body） |
| `service/RagPromptBuilder` | 把「系统提示（导购人设+严格 grounding）+ 用户问题 + 候选商品上下文」拼成 messages | 纯函数 |
| `config/RagProperties` | 绑定 `rag.*`（qwen key/base-url/model/temperature、topK、context-size、超时） | — |
| `config/RagExecutorConfig` | 专用 `TaskExecutor`（`ragStreamExecutor`）跑后台流式线程 | — |
| **复用** `SemanticSearchService` | 检索 top-K 商品（内部已含 embedding + Qdrant + 关键词降级） | — |
| **复用** `ProductListItemResponse` | 商品卡结构（id/name/price/stock/imageUrl/categoryName） | — |
| `config/SecurityConfig`（改） | 显式放行 `GET /api/products/qa`（注：现有 `/api/products/*` GET 规则已覆盖之，此为显式标注；Spring 路由中字面量 `qa` 优先于 `{productId}` 变量，无冲突） | — |
| `application.yml`（改） | 增 `rag.*` 配置块，qwen key 复用 `DASHSCOPE_API_KEY` 注入 | — |

**呼应现有模式**：抽象接口 = `PaymentGateway`/`EmbeddingClient`；检索复用 = `SemanticSearchService`；外部 REST = AI-1 的 `RestClient` 封装。

## 数据流与 SSE 事件协议

**请求**：`GET /api/products/qa?q=<问题>&topK=<可选>`（开放）。

**后端流程**（`RagService.answer`）：

```
1. products = semanticSearchService.search(q, null, null, null, topK)   // 复用 AI-1
2. 若 products 为空：
     SSE: event:products data:[]
        → event:answer  data:"没找到相关商品，换个说法试试？"
        → event:done
     emitter.complete()          // 不调 qwen，省 token
3. 否则：
     a. SSE: event:products data:<商品 JSON 数组>     // 前端立即渲染卡片
     b. messages = promptBuilder.build(q, products)
     c. ragStreamExecutor 后台线程：
          chatClient.streamChat(messages, delta -> SSE event:answer data:<delta>)
     d. 正常结束 → event:done → emitter.complete()
        出错      → event:error → emitter.completeWithError()
```

**SSE 事件协议**（`text/event-stream`）：

| event 名 | data 内容 | 时机 |
|---|---|---|
| `products` | 商品卡 JSON 数组（`ProductListItemResponse[]`） | 检索后最先推一次（空也推 `[]`） |
| `answer` | 一段增量文本（qwen delta） | 生成过程中多次 |
| `done` | 可选汇总（token 用量）或空 | 生成正常结束，最后一次 |
| `error` | 错误简述（不含密钥/堆栈） | 生成中断时替代 done |

**前端用法**：`new EventSource('/api/products/qa?q=...')`，监听 `products`（渲染卡片）/`answer`（追加打字机文本）/`done`（收尾关闭）/`error`（提示）。

**设计要点**：先 `products` 后 `answer`，用户「秒看到商品、再看到逐字推荐」；空结果不烧 token；后台线程推流，主线程不阻塞。

## Prompt 构造与 grounding

`RagPromptBuilder.build(q, products)` 产出两条 message：

- **system**（固定模板，导购人设 + 严格约束）：
  > 你是「南鲸商城」的导购助手。**只能**根据下面提供的「候选商品」回答和推荐，**不得编造**清单之外的商品或参数。结合用户需求说明为什么推荐、点出差异（价格/品类/适用场景）。简洁、口语、友好；信息不足就说明。

- **user**：用户问题 + 候选商品上下文（紧凑文本，带序号便于引用）：
  ```
  用户问题：想要适合夏天解渴的饮料，30 元以内
  候选商品：
  1. 蓝鲸气泡水 海盐柚子 330ml*6 | 饮料 | ￥29.90
  2. 冷萃黑咖啡 无糖 200ml*4 | 饮料 | ￥39.90
  ```

要点：grounding 全靠 system 约束 + 只喂检索结果（不喂全库）；上下文商品数取 `rag.context-size`（默认等于 `topK`，建议 5~8，控制 token）。

## 错误处理

| 场景 | 处理 |
|---|---|
| 检索为空 | 短路：推 `products:[]` + 一句 `answer` + `done`，不调 qwen |
| 检索失败（embedding/Qdrant 挂） | `SemanticSearchService` 内部已降级关键词搜索；若仍异常 → 推 `error` 事件 |
| qwen 超时/报错/限流/余额不足 | `products` 已先推（前端有卡片）→ 再推 `error`（「生成繁忙，请稍后再试」），`completeWithError` |
| qwen 流中途断 | 已推的 `answer` 保留 → 补 `error` 收尾 |
| 密钥/安全 | qwen key 复用 `DASHSCOPE_API_KEY`（环境变量注入，不入库）；错误信息不带密钥/堆栈 |

## 配置（rag.*）

| 键 | 默认 | 说明 |
|---|---|---|
| `rag.qwen.api-key` | `${DASHSCOPE_API_KEY:}` | 通义/百炼 key（**与 embedding 共用**，环境变量注入） |
| `rag.qwen.base-url` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | DashScope OpenAI 兼容端点根 |
| `rag.qwen.model` | `qwen-plus` | 生成模型 |
| `rag.qwen.temperature` | `0.3` | 采样温度 |
| `rag.top-k` | `6` | 检索条数 |
| `rag.context-size` | `6` | 喂给 LLM 的最多商品数 |
| `rag.timeout-ms` | `30000` | qwen 请求超时 |
| `rag.emitter-timeout-ms` | `60000` | SseEmitter 超时 |

> 切换到 DeepSeek（或其它 OpenAI 兼容厂商）只需改 `base-url`/`model`/`api-key` 三项配置，代码不动。

## 测试策略

- **单元（纯 Mockito）**：
  - `RagServiceTest`：mock `SemanticSearchService` + `ChatCompletionClient`，验证 ①空结果短路（不调 chatClient、推空 products + done）②正常路径事件顺序（先 products、后多次 answer、最后 done）③生成异常 → 推 error。用假 `SseEmitter` 或捕获回调断言事件序列。
  - `RagPromptBuilderTest`：纯函数，验证 system 约束文案 + 候选商品正确拼入 user message、上下文条数受 `context-size` 限制。
- **薄 HTTP 封装** `QwenChatClient`：SSE chunk 解析逻辑可单测（喂样例 chunk 串，断言 delta 提取与 `[DONE]` 终止）；真实 qwen 调用不进自动化测试（需 key），联调手动验。
- **不写**重型集成测试（SSE 端到端 + 真 qwen 依赖外部 key，跳过；与 AI-1「Qdrant 可用才跑」同思路）。

## 新增依赖与基础设施

- **零新增 Maven 依赖**：`RestClient`（流式读 body）+ Spring MVC `SseEmitter`，均已在栈内。
- 外部依赖：通义千问（qwen，DashScope 兼容端点）；**embedding 与生成共用同一个 `DASHSCOPE_API_KEY` / 百炼账号**。
- 无数据库迁移（不持久化问答）。

## 文件清单（预估）

| 文件 | 动作 |
|---|---|
| `service/ChatCompletionClient` + `impl/QwenChatClient` | 创建 |
| `service/RagService` + `impl/RagServiceImpl` | 创建 |
| `service/RagPromptBuilder` | 创建 |
| `controller/RagController` | 创建 |
| `config/RagProperties`、`config/RagExecutorConfig` | 创建 |
| `config/SecurityConfig`、`application.yml` | 修改 |
| 对应单元测试 | 创建 |

## 后续加固点（不在本期）

- 多轮对话 + 对话历史（可复用客服模块的会话存储思路）。
- 混合检索 + rerank 提升候选质量；引用编号回链到具体商品。
- 流式 token 计量 / 限流 / 缓存常见问题。
- 把 RAG 接入实时客服（任务 7）作为「AI 客服」。
- 生成侧可随时切回 DeepSeek（充值后改配置即可）。
