# 学习笔记：AI 应用三连——语义搜索、RAG 导购问答、导购 Agent

> 本文是配合南鲸商城练手项目 **AI 方向（AI-1 / AI-2 / AI-3）** 的**知识沉淀**，面向第一次做「把大模型接进业务系统」的同学，方便后续复盘。
> 三个子项目是**层层叠加**的关系：AI-1 建立**向量检索基座** → AI-2 在其上叠加**大模型生成**（RAG）→ AI-3 再叠加**多轮工具调用 + 生产化护栏**（Agent）。读的时候按顺序来，后面处处复用前面。
> 实现细节落在 `实现说明.md` 的三节，设计取舍落在 `重要决策说明.md` #57~#64，本文只讲**原理与工程权衡**。手册里的对应小节是 [项目学习手册.md](./项目学习手册.md) §5.14~§5.16。

---

## 开篇：先建立三个概念的关系

初学者最容易把「语义搜索 / RAG / Agent / 大模型」搅成一团。先用一句话各自定位：

| 概念 | 一句话 | 本项目对应 |
|---|---|---|
| **embedding（嵌入）** | 把一段文字变成一串数字（向量），语义相近的文字向量也相近 | 通义 `text-embedding-v3`（1024 维） |
| **向量检索 / 语义搜索** | 用向量的「距离」找语义最像的东西 | AI-1，`GET /api/products/semantic` |
| **RAG（检索增强生成）** | 先检索真实资料，再让大模型**基于资料**回答（防它瞎编） | AI-2，`GET /api/products/qa` |
| **Agent（智能体）** | 让大模型**自主决定分几步、调哪些工具**来完成任务 | AI-3，`GET /api/assistant/chat` |

一个形象的递进：

- **语义搜索** = 一个很懂你意思的**搜索框**（返回商品列表）。
- **RAG** = 搜索框 + 一个**导购员**，他看着搜出来的真货跟你说「给您推荐这几款……」。
- **Agent** = 一个**能自己跑腿的导购员**，他会「先去搜商品 → 再跑去查库存 → 再翻你的历史订单」，几步做完再回来回答你。

---

# 第一部分：AI 语义搜索（AI-1）

> 对应实现 `实现说明.md`「AI 语义搜索：outbox 同步与向量检索」、决策 #57、手册 §5.14。

## 1. 关键词搜索差在哪

项目原本的搜索是 `GET /api/products`，本质是 SQL 的 `WHERE name LIKE '%关键词%'`。它只认**字面**：

- 搜「降噪耳机」，商品叫「无线蓝牙耳机」→ **搜不到**（字面不含「降噪」）。
- 搜「做饭用的调味料」→ **搜不到**橄榄油、酱油（它们名字里根本没有「调味料」三个字）。

问题的根子：**关键词搜索不懂「语义」**，它不知道「降噪」和「蓝牙耳机」相关、「调味料」和「酱油」是一类东西。

## 2. embedding：把「意思」变成一串数字

**语义搜索的地基是 embedding**：用一个训练好的模型，把任意一段文字映射成一个**固定长度的向量**（本项目是 1024 个浮点数）。这个向量的神奇之处：

> **语义相近的文字，向量在空间里也靠得近。**

比如「酱油」和「醋」的向量距离，会明显小于「酱油」和「充电器」。于是「找语义最像的商品」就变成了数学问题：**在向量空间里找离查询向量最近的那些商品向量**。「近」怎么算？最常用**余弦相似度**（呼应推荐算法那篇笔记里的余弦——同一个数学工具，这里换了个场景用）。

本项目 embedding 文本 = 「商品名 + 类目名」（`product` 表没有描述字段，这是留的加固点：以后加了商品描述，召回会更准）。

> 💡 你不需要懂 embedding 模型内部怎么训练的，就像你不需要懂 MySQL 的 B+ 树也能用索引。**会用 + 知道它的语义特性**就够了。

## 3. 向量库 Qdrant：为什么不塞进 MySQL

有了向量，存哪儿、怎么查最近邻？三个候选：

| 方案 | 怎么做 | 问题 |
|---|---|---|
| A. 存 MySQL，Java 里算 | 向量存 BLOB，查询时全表拉出来逐个算余弦 | 商品一多就是 O(N) 全表扫，慢、吃内存 |
| B. Redis 存向量 | 手搓，无原生向量检索 | 造轮子，且没有近邻索引 |
| **C. 专用向量库 Qdrant**（本项目选） | 它内建**近似最近邻（ANN）索引**，专为「海量向量里快速找最近的 K 个」而生 | 需多起一个 Docker 容器 |

**选 C 的理由**：学习真实工业界方案；Qdrant 支持**过滤 + 近邻检索一次做完**（见第 5 节的「过滤下推」）；而且用 `productId` 作 point id，天然幂等（见第 4 节）。用 Spring `RestClient` 直调它的 REST API，**不引任何 Qdrant SDK，零新增 Maven 依赖**——和本项目一贯的「轻依赖」取向一致。

> 关键认知：**MySQL 仍是商品的「真相来源」，Qdrant 只是一份「向量索引」**。Qdrant 里的数据丢了，随时能从 MySQL 重建（`POST /api/admin/products/reindex` 全量回填）。这和推荐笔记里「相似度矩阵是派生数据」是同一个思想——**派生数据不必当命根子，能重建就行**。

## 4. 核心难点：两个库怎么保持一致（transactional outbox）

这是 AI-1 **最值得学**的一块，比 embedding、比 Qdrant 的 API 都重要。

**问题场景**：商品增删改发生在 MySQL，向量索引在 Qdrant。商家改了个商品名，两个库都得更新。天真写法：

```java
productMapper.updateById(product);   // 1. 写 MySQL
qdrant.upsert(embed(product));       // 2. 写 Qdrant —— 万一这步失败呢？
```

第 2 步如果网络抖动 / Qdrant 宕机 / 通义超时失败了，就会**数据不一致**：MySQL 是新名字，Qdrant 还是旧向量。而且更糟——**商品写操作被 AI 链路拖累**：通义慢，用户改个商品名就卡半天；Qdrant 挂了，商品干脆改不了。这显然不能接受：**AI 是锦上添花，绝不能让它拖垮核心的商品管理**。

**解法：transactional outbox（事务性发件箱）**。核心思想是**「先在本地事务里记一笔『待办』，再由后台异步去补」**：

```
写入路径（生产者）——只碰 MySQL，一个事务：
  @Transactional {
    写 product 表
    写 index_outbox 表一条事件(op=UPSERT, status=PENDING)   ← 关键：待办也进 MySQL
  }
  → 两张表同事务原子提交。Qdrant/通义此刻挂了也无所谓，商品照样写成功。

中继路径（消费者）—— OutboxRelayJob，@Scheduled 每 5s：
  拉一批 status=PENDING 的事件
  → embed（调通义）→ upsert 到 Qdrant
  → 成功：标记 DONE；失败：retry_count+1，超 5 次标 FAILED
```

**为什么这样就可靠？** 三个关键性质：

1. **原子性**：商品写和「待同步事件」写在**同一个数据库事务**里，要么都成功、要么都回滚。绝不会出现「商品改了但没人记得要同步向量库」。
2. **解耦可用性**：写商品的事务**只碰 MySQL**，完全不依赖通义/Qdrant 在线。AI 基础设施全挂，商品管理照常工作，事件堆在 `index_outbox` 里等中继慢慢消费。**主站可用性 ⊥ AI 可用性**。
3. **至少一次投递 + 幂等**：中继可能失败重试，甚至「处理成功了但标 DONE 前崩溃」，导致同一事件被处理两次。靠什么兜底？**幂等**——Qdrant 以 `productId` 作 point id，`upsert` 重复只是覆盖同一个点、`delete` 重复无副作用。所以重复处理结果一致，不怕。

**状态机**：`PENDING →（中继成功）→ DONE`；失败未超限留 `PENDING` 重试；失败超限 `→ FAILED`（等人工或 reindex 修复）。

> 🔴 **一个容易忽略的乱序处理**：如果「UPSERT 事件还没消费，商品就被删了」，中继处理那条 UPSERT 时会发现 `productMapper.selectById` 查不到商品 → **自动退化成 delete**。这种「补偿/降级」思维在异步系统里很常见。

**这个模式的迁移价值极高**。凡是「本地库写 + 要可靠地通知外部系统」——发消息队列、建搜索索引（ES）、调下游服务、发通知——都能用 outbox。记住它，比记住 Qdrant 的 API 有用得多。

> 对比一下你可能听过的其他方案：
> - **双写（天真写法）**：就是上面那个会不一致的错误示范。
> - **CDC / binlog（如 Debezium）**：监听 MySQL binlog 同步下游，更解耦但要搭额外组件，重。
> - **outbox**：折中——不用额外中间件，靠一张表 + 一个定时中继，代码可控、适合学习和中小项目。

## 5. 查询路径：过滤下推 + 优雅降级

查询侧 `GET /api/products/semantic?q=...&categoryId=&minPrice=&maxPrice=&topK=`：

```
1. embed(查询词) → 查询向量
2. Qdrant 检索：向量近邻 + 分类/价格「过滤下推」，一次做完
3. 命中按 score 降序拿 productId → 批量回表 → 按命中顺序重排 → 返回商品卡
4. 任一步抛异常 → 降级为关键词搜索 productService.searchProducts(...)
```

两个要点：

- **过滤下推（filter push-down）**：分类、价格区间的过滤**交给 Qdrant 在检索时一起做**（作为 payload 过滤），而不是「取回 topK 再在 Java 里筛」。为什么重要？如果先取 10 个最近邻再筛价格，可能筛完只剩 3 个；下推则是**从一开始就只在符合条件的向量里找最近的 10 个**，结果更对。
- **优雅降级**：embedding 或 Qdrant 任一挂了，`catch` 住，自动回退关键词搜索。**对前端完全透明**——返回结构一模一样，前端根本不知道后端降级了。这就是「AI 是增强、不是依赖」的落地：AI 全挂，搜索功能仍可用，只是没那么「聪明」。

> **真实联调验证过**（查询词在商品名里一个字都不出现，纯语义召回）：
> 「做饭用的调味料」→ 橄榄油 / 酱油；「夏天解渴的饮料」→ 气泡水 / 黑咖啡；「送朋友的健康礼物」→ 坚果 / 饼干。关键词搜索做不到这些。

---

# 第二部分：RAG 导购问答（AI-2）

> 对应实现 `实现说明.md`「AI 导购问答（RAG）」、决策 #58、手册 §5.15。

## 1. 为什么不能直接把问题丢给大模型

语义搜索只能返回一串商品卡。但用户更想要一句**导购话术**：「给爸妈买健康年货，预算 200」→「推荐每日坚果和手工饼干，都是健康零食，两样加起来 180……」。

直接问大模型（`qwen-plus`）行不行？**不行，它会「幻觉」**：

- 大模型**不知道你店里有什么货**、什么价、有没有库存。
- 你问它，它会**一本正经地编**出「南鲸牌有机核桃礼盒 ¥199」——听起来很真，但你店里**根本没这个商品**。用户下单就穿帮了。

根因：大模型的知识来自训练语料，**不包含你的实时业务数据**，硬答就会编。

## 2. RAG = 检索 + 增强 + 生成

**RAG（Retrieval-Augmented Generation，检索增强生成）** 就是治幻觉的主流方案，三步：

```
Retrieval（检索）：先用语义搜索（复用 AI-1！）找到真实存在的候选商品
Augmentation（增强）：把这些真实商品塞进给大模型的「提示词（prompt）」里
Generation（生成）：让大模型「只根据这些商品」生成推荐话术
```

一句话：**给大模型「开卷考试」**——先把「参考资料」（检索到的真货）递给它，它照着资料答，就不会瞎编了。

```
用户问题 q
  │
  ├─ semanticSearchService.search(q)  → top-K 真实商品（复用 AI-1）
  │
  ├─ RagPromptBuilder 构造两条 message：
  │    system: 「你是导购，只能从下面候选商品里推荐，不得编造清单外商品」
  │    user:   「用户问题：{q}。候选商品：1.坚果 ¥89 …  2.饼干 ¥45 …」
  │
  └─ qwen-plus 流式生成 → 边生成边推给前端
```

## 3. grounding：怎么「摁住」大模型不瞎编

「让它只根据资料答」在工程上叫 **grounding（接地/锚定）**，靠两件事一起保证：

1. **只喂检索结果**：给大模型的上下文里**只有**检索到的真实商品，不喂全库、不留自由发挥空间。
2. **system 提示强约束**：system message 里明确写死「**只能**根据下列候选商品推荐、**不得**推荐清单之外的任何商品」。

两者缺一不可：只喂资料但不约束，它可能自作主张加货；只约束不喂资料，它没东西可推。

## 4. SSE 流式：为什么要「打字机」效果

大模型生成是**逐字（token）**吐出来的。如果等它整段生成完再一次性返回，用户要干瞪眼好几秒。**SSE（Server-Sent Events）** 让服务端能**边生成边推**，前端做「打字机」追加，体验好得多。

本项目用 Spring MVC 的 `SseEmitter` 实现（**不引 WebFlux 响应式栈，零新增依赖**）：

```java
@GetMapping(value="/api/products/qa", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter qa(@RequestParam String q, @RequestParam(defaultValue="5") int topK) {
    SseEmitter emitter = new SseEmitter(timeoutMs);
    ragStreamExecutor.execute(() -> ragService.answer(q, topK, emitter)); // 活儿丢给后台线程池
    return emitter;   // 主线程秒返回，不占用容器请求线程
}
```

**关键：主线程立即返回 `SseEmitter`，真正的生成在后台线程池里跑。** 否则请求线程会被长时间占住，并发一高容器线程就耗尽了。

**SSE 事件协议**（前端按 `event` 名分别处理）：

```
products  （先推：检索到的商品卡数组，前端立即渲染卡片）
answer    （多次：回答的增量 token，前端追加成打字机）
answer
answer …
done      （结束）
error     （任一步失败时替代，推提示文案）
```

先推 `products` 再推 `answer`，用户「秒看到商品、再看到逐字推荐」，不会白屏等待。

## 5. 两个省心设计

- **空结果短路**：检索不到任何商品，就**不调大模型**了，直接回一句「没找到相关商品」+ `done`。调大模型要花钱（token 计费），能省则省。
- **可插拔生成**：`ChatCompletionClient` 是接口（呼应 AI-1 的 `EmbeddingClient`、支付的 `PaymentGateway`），现在实现是 `QwenChatClient`。想换 DeepSeek？只改配置 `base-url`/`model`/`api-key`，业务代码一行不动——因为它们都遵循 **OpenAI 兼容**协议。

> 小插曲：路线图原计划生成侧用 DeepSeek，但联调时其账户余额不足（返回 402），于是改用通义千问 `qwen-plus`，**复用 embedding 的同一个 `DASHSCOPE_API_KEY`**，零额外成本。接口抽象在这时就体现价值了——换模型毫无痛感。

---

# 第三部分：AI 导购 Agent（AI-3）

> 对应实现 `实现说明.md`「AI 导购 Agent：手写 Agent 循环 + 工具集 + 生产化护栏」、决策 #59~#64、手册 §5.16。

## 1. 从「单轮」到「多轮」：Agent 到底是什么

RAG 是**单轮**的：检索一次 → 回答一次。但真实导购是**多轮、要跑腿**的。用户问：「有没有适合送人、200 块以内、还有货的礼盒？」——一个真人导购会：

```
① 先搜「送人的礼盒」有哪些   → ② 逐个查价格是否 ≤200
→ ③ 查这些还有没有库存        → ④ 综合起来回答
```

这需要让大模型**自己决定**：分几步、每步调用什么能力。**这就是 Agent**。

> **一句话定义：Agent = 大模型 + 循环 + 工具。**
> 让大模型在一个 `while` 循环里反复「思考 → 决定调哪个工具 → 看工具返回的结果 → 再思考」，直到它认为「我可以回答了」，才生成最终答复。

别把 Agent 想得很玄。你看懂下面这个循环，就理解了它的 80%。

## 2. 工具调用（function calling）：大模型怎么「调函数」

现代大模型支持 **function calling / tool calling**：你在请求里告诉它「你有这些工具可用」（每个工具的名字、说明、参数），它在回答里可以**不直接答，而是输出一个「我要调用 `search_products('礼盒')`」的结构化请求**。

- 注意：**大模型本身不执行任何代码**，它只是「说」它想调哪个工具、传什么参数。
- **真正执行工具的是你的后端**（`ToolRegistry.execute`）。执行完把结果**再喂回给大模型**，它接着往下想。

这就是「循环」的由来：大模型说要调工具 → 后端执行 → 结果回填 → 大模型再决定（继续调别的工具 / 还是可以回答了）。

## 3. 手写 Agent 循环

本项目**手写**这个循环，不引 Spring AI / LangChain4j 框架（决策 #59：这些框架 API 还在频繁变动，核心学习目标就是「自己实现一遍」，手写更透明可控）：

```
初始化 AgentContext（从登录身份 AuthUser 取 userId / role）
构造 system message（导购人设 + 工具说明 + grounding 约束）
round = 0
WHILE round < maxRounds(5):
    turn = agentChatClient.chat(messages)      // 非流式，拿这一轮的完整决定
    IF turn 里有 tool_calls:                    // 大模型决定调工具
        FOR EACH 工具调用 tc:
            推 SSE "step"  （执行前说明意图，如「正在搜索商品…」）
            result = ToolRegistry.execute(tc.name, tc.args, ctx)
            推 SSE "tool"  （工具结果，ok=true/false）
            IF 是商品类工具: 推 SSE "products"（商品卡数组）
            把 result 作为一条 message 追加进对话历史
            IF 客户端已断开: 短路退出（省 token）
    ELSE:                                       // 大模型不调工具了 = 它能回答了
        agentChatClient.streamFinal(messages)   // 流式推 "answer"
        推 SSE "done"; 结束
    round++
IF round 到顶还没收敛:
    推 SSE "error"（「需要更多步骤…」）
```

**几个细节**：

- **工具轮用非流式、最终轮用流式**：调工具阶段需要拿到完整的结构化 `tool_calls` 才能执行，用非流式；最后生成给用户看的答复才需要打字机效果，用流式 `streamFinal`。
- **`maxRounds=5` 是必要的护栏**：万一大模型陷入「反复调工具就是不回答」的循环，5 轮封顶，超了推 `error`，防止无限烧 token。
- **客户端断开就短路**：前端关了页面（`SseEmitter.onCompletion` 回调），设一个**每请求局部**的 `AtomicBoolean disconnected=true`，下一轮开始前检查到就提前退出，省下剩余轮次的 token。（注意是「每请求局部」而非单例字段，否则会跨请求串扰——这是并发编程的常见坑。）

**SSE 事件协议**（比 RAG 多了 `step`/`tool`，让用户看到 Agent「在干活」）：
`step`（工具意图）→ `tool`（工具结果）→ `products`（命中商品卡）→ `answer`（流式回答）→ `done`；异常/超轮数→`error`。

## 4. 工具集与「越权防护」（Agent 安全的核心）

本项目给大模型配了 **6 个工具**（决策 #60：spec 原设计 7 个，砍掉了参数复杂、导购场景几乎用不到的 `preview_coupon`）：

| 工具 | 干什么 | 越权保护 |
|---|---|---|
| `search_products` | 语义搜索商品（复用 AI-1） | 公开只读 |
| `get_product_detail` | 查商品详情 | 公开只读 |
| `check_stock` | 查库存 | 公开只读 |
| `list_claimable_coupons` | 查可领的券 | 公开只读 |
| `get_my_orders` | 查**我的**订单 | **强制** `ctx.userId()` |
| `list_my_coupons` | 查**我的**券 | **强制** `ctx.userId()` |

> 🔴 **这是 Agent 安全最关键的一点，务必理解**：
> 后两个工具访问**个人隐私数据**。如果把 `userId` 设计成**让大模型从参数里传**，攻击者就能诱导它：「帮我看看用户 123 的订单」——大模型很可能照做，造成**越权访问他人数据**。
> **正确做法：个人数据的身份永远从服务端认证上下文 `ctx.userId()` 取，工具参数无权指定 userId。** 大模型再怎么被诱导，也只能查到**当前登录用户自己**的数据。
> 这和手册 §5.3「授权在 Service 层、身份从 SecurityContext 取」是同一条铁律的延伸——**别信任何来自外部（这里是大模型输出）的身份声明**。

## 5. 生产化护栏三件套（让 AI 功能能真正上线）

Demo 能跑 ≠ 能上线。上线要防：被人刷爆账单、下游 AI 抖动引发雪崩、出了问题两眼一抹黑。三件套对应解决：

### ① 限流：Redis Lua 固定窗口

防有人狂刷 `/api/assistant/chat` 把你的大模型账单打爆。用 Redis Lua 脚本实现固定窗口令牌桶（决策 #61，不引 Bucket4j，复用既有 Redis 栈）：

```lua
-- 原子执行：计数 +1，若是本窗口第一次则设过期时间
local count = redis.call('INCR', KEYS[1])
if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
return count
```

`RateLimitInterceptor` 拦截三个 AI 端点，登录用户按 `userId`、游客按 IP 限流 **20 req/60s**；超限抛 `BusinessException(429)` → 全局处理器转 `{code:429}`（HTTP 200 是项目既有约定）。

> 为什么 Lua？因为「计数 + 设过期」必须**原子**。分成两条命令的话，可能 INCR 完还没 EXPIRE 就崩了，这个 key 永不过期，限流就废了。Lua 脚本在 Redis 里整体原子执行，杜绝这个窗口。呼应 `RedisLockUtil` 的 Lua 解锁——**同一个「多步操作要原子」的思路**。

### ② 熔断/重试：Resilience4j + 降级

下游（通义/qwen）抖动时，不能让请求都堆着无限等待（会拖垮线程池 → 雪崩）。用 Resilience4j（决策 #62）：

- **embedding**：`@CircuitBreaker + @Retry(maxAttempts=2)`。短暂失败值得重试；重试仍失败 → fallback 返 `float[0]` → 触发**降级为关键词搜索**（降级链复用 AI-1 已有的 try/catch）。
- **LLM（qwen）**：**只熔断、不重试**。fallback 抛异常 → 上层推 SSE `error`。
  - **为什么 LLM 不重试？** 大模型调用**又慢又贵**，而且 SSE 连接已经开着，重试会让用户**长时间干等**。「快速失败、推个 error 让用户重发」比死等体验好。

> 熔断器（Circuit Breaker）像家里的保险丝：下游连续失败到阈值就「跳闸」（开路），之后的请求**直接走 fallback 不再打下游**，给下游喘息时间；过一阵「半开」试探，恢复了再合闸。防止「一个下游挂了，请求全卡在它那儿，最终拖垮整个应用」的**级联失败**。

### ③ 监控：Micrometer + Actuator/Prometheus

出问题要能看见。用 Micrometer 埋三个指标，经 Actuator 的 `/actuator/prometheus` 暴露给 Prometheus 抓取：

| 指标 | 类型 | 看什么 |
|---|---|---|
| `ai.tool.invocations`（tag: tool, ok） | Counter | 每个工具调了多少次、成功率 |
| `ai.agent.rounds` | DistributionSummary | Agent 平均要几轮收敛（太高说明工具设计有问题） |
| `ai.ratelimit.rejected` | Counter | 有多少请求被限流拒了 |

> 🔴 **一个鉴权坑（决策 #63）**：`/actuator/prometheus` 只让 ADMIN 访问，`SecurityConfig` 里要用 `hasAuthority("ADMIN")` 而**不是** `hasRole("ADMIN")`。因为本项目 JWT 存的是**裸角色串** `"ADMIN"`（无 `ROLE_` 前缀），而 `hasRole("ADMIN")` 内部会自动查 `"ROLE_ADMIN"` → 永远匹配不上 → 鉴权恒拒。这个坑很隐蔽，`hasAuthority` 才是按原始串比较。
>
> 🔴 **一个安全加固（决策 #64，终评发现）**：加了 ADMIN-only 的 `/prometheus` 后暴露出一个**旧漏洞**——注册接口 `register` 原来直接采信客户端传入的 `role` 字段，于是**任何人都能自注册成 ADMIN**，进而访问 Prometheus 等 ADMIN 接口（**权限提升漏洞**）。修复：`register` 强制分配 `CUSTOMER` 角色，忽略客户端传的 role；ADMIN/STAFF 只能由种子数据或后台分配。**教训：开放注册端点绝不能让客户端自选角色。**

---

## 附：三块 AI 应用的共同工程思想

虽然语义搜索、RAG、Agent 各有侧重，但贯穿始终的是同一批可迁移的工程原则——**这些比任何具体 API 都值得记住**：

1. **AI 是增强，不是依赖**：三块都做了「AI 挂了怎么办」——语义搜索降级关键词、RAG/Agent 推 `error`、embedding 熔断降级。**核心功能的可用性绝不能建立在 AI 可用之上。**
2. **用 outbox 解耦可靠同步**：主库写 与 外部系统同步（向量索引）分离，同事务记事件、异步中继补，兼顾一致性与可用性。
3. **RAG 防幻觉靠 grounding**：只喂检索到的真实数据 + 强约束提示，让大模型开卷考试而非自由发挥。
4. **Agent 就是「大模型 + 循环 + 工具」**：手写一遍就懂；配 `maxRounds` 防失控，断开就短路省 token。
5. **别信外部身份声明**：个人数据工具的 `userId` 只从服务端上下文取，大模型参数无权指定——防越权。
6. **面向接口 + OpenAI 兼容**：`EmbeddingClient`/`ChatCompletionClient`/`AgentChatClient` 全是接口，换模型只改配置。
7. **生产化三件套**：限流（防刷 + 防账单）、熔断降级（防雪崩）、监控（可观测）——demo 到上线的必经之路。
8. **原子操作用 Lua**：凡「多步 Redis 操作要原子」（限流计数+过期、分布式锁判断+删除），都用 Lua 脚本。

> 与另外两篇学习笔记的呼应：**余弦相似度**（推荐 vs 语义检索复用同一数学工具）、**面向接口留扩展缝**（`PaymentGateway` → `EmbeddingClient`/`ChatCompletionClient`）、**Lua 保证原子**（`RedisLockUtil` → `RateLimitUtil`）、**系统线程无 SecurityContext**（支付回调/定时任务 → outbox 中继/RAG 后台线程）。整个项目的工程手法是**一以贯之**的。
