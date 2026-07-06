# BlueWhale Project

## 新会话必读

每次新会话开始时先读 **`docs/上手指南.md`**，其中说明了：

- 需要读哪些文件才能快速了解项目当前进度
- 实现代码或做出技术决策后必须更新哪些文档

---

## Tech Stack

| Layer      | Technology                                               |
| ---------- | -------------------------------------------------------- |
| Language   | Java 17                                                  |
| Framework  | Spring Boot 3.4.0                                        |
| Build Tool | Maven                                                    |
| ORM        | MyBatis Plus 3.5.9                                       |
| Security   | Spring Security (stateless / JWT-ready)                  |
| Database   | MySQL 8.x                                                |
| Validation | Jakarta Bean Validation (spring-boot-starter-validation) |
| Utility    | Lombok, Spring Boot DevTools                             |
| Resilience | Resilience4j 2.2.0 + spring-boot-starter-aop (CB/Retry on AI clients) |
| Monitoring | Spring Boot Actuator + Micrometer + micrometer-registry-prometheus |

## Project Info

- **Group**: com.twentyzhang
- **Artifact**: bluewhale
- **Root Package**: com.twentyzhang.bluewhale

## Directory Structure

```
src/
└── main/
    ├── java/com/twentyzhang/bluewhale/
    │   ├── BluewhaleApplication.java   # Entry point
    │   ├── controller/                 # REST controllers (@RestController)
    │   ├── service/                    # Business logic interfaces and impls
    │   ├── repository/                 # MyBatis Plus mapper interfaces
    │   ├── entity/                     # Database entity classes (@TableName)
    │   ├── dto/                        # Request/Response data transfer objects
    │   ├── config/                     # Spring configuration classes
    │   │   ├── MybatisPlusConfig.java  # Pagination interceptor
    │   │   └── SecurityConfig.java     # Spring Security filter chain
    │   └── exception/                  # Exception handling
    │       ├── BusinessException.java  # Custom runtime exception
    │       └── GlobalExceptionHandler.java  # @RestControllerAdvice
    └── resources/
        └── application.yml             # App configuration (DB, MyBatis Plus, logging)
```

## Key Configuration Notes

- **Security**: Stateless session (STATELESS). `/api/auth/**` is open; all other endpoints require authentication. `GET /api/assistant/chat` is NOT in the guest-open list — it requires login.
- **Actuator**: Exposed endpoints: `health` (permitAll) and `prometheus` (requires `hasAuthority("ADMIN")` — bare string, NO `ROLE_` prefix, matching `JwtAuthenticationFilter` behavior). `/actuator/health` is explicitly permitAll in `SecurityConfig`; `/actuator/**` falls under `hasAuthority("ADMIN")`.
- **Rate Limiting**: `RateLimitInterceptor` covers `/api/assistant/chat`, `/api/products/qa`, `/api/products/semantic` — 20 req/60s per userId (logged-in) or IP (guest). Over-limit returns `{code:429}` via `BusinessException(429)` → `GlobalExceptionHandler` (HTTP 200, project convention).
- **MyBatis Plus**: Pagination interceptor enabled for MySQL. Underscore-to-camelCase mapping on. Logical delete via `deleted` field (1=deleted, 0=active).
- **Database**: Configure `spring.datasource.url/username/password` in `application.yml` before running.

## 本地依赖服务（Local Dependencies）

应用启动依赖以下本地服务，端口写在 `application.yml`（均可用环境变量覆盖）：

| 服务 | 端口 | 用途 | 不可用时影响 |
|---|---|---|---|
| MySQL | 3305 | 主数据源（Flyway 启动自动迁移 V1~V7） | 应用无法启动 |
| Redis | 6379 | 缓存 / 分布式锁 / 在线状态 | 相关功能不可用 |
| **Qdrant** | 6333（REST）/ 6334（gRPC） | **AI 语义搜索向量库（模块十四）** | 语义搜索降级为关键词搜索，应用仍正常启动 |

### Qdrant（向量库，Docker）

- **镜像/容器**：`qdrant/qdrant:latest`，容器名 `qdrant`。Collection `products`（1024 维 / Cosine）由应用 `ensureCollection` 自动创建，无需手动建。
- **首次创建**（仅一次，挂命名卷持久化）：
  ```bash
  docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant
  ```
- **日常启动**（容器已存在、重启电脑后）：`docker start qdrant`
- **可视化**：浏览器开 http://localhost:6333/dashboard
- **自动启动**：本项目已在 `.Codex/settings.local.json` 配了 `SessionStart` 钩子，**每次新会话 Codex 会自动 `docker start qdrant`（不存在则用上面带卷的 `docker run` 重建）**，前提是 Docker Desktop 已开启。Docker 未开时钩子静默跳过、不阻塞会话。
- **数据持久化**：向量存于命名卷 `qdrant_storage`（挂到容器 `/qdrant/storage`）。`docker stop/start`、`docker rm` 重建均不丢数据；卷重新挂上即恢复。彻底清空：`docker volume rm qdrant_storage`（删卷后需重新 `reindex` 回填）。
- 联调真实语义召回还需通义 DashScope 的 `DASHSCOPE_API_KEY`（仅 embedding 用；单测/集成测试用桩，不需 key）。阿里百炼 workspace key（`sk-ws-…`）在公网 DashScope 端点即可跑 `text-embedding-v3`，只设 `DASHSCOPE_API_KEY` 即可，无需改 URL。
- **怎么测语义搜索 / 真实联调结果**：见 `docs/实现说明.md`「AI 语义搜索 → 本地联调与验证」一节（启动→reindex→`GET /api/products/semantic?q=...` 步骤 + 验证结果）。
- **AI 导购问答（RAG，模块十五）**：`GET /api/products/qa?q=...`（SSE 流式）。生成侧通义千问 `qwen-plus`，**复用同一个 `DASHSCOPE_API_KEY`**（与 embedding 共用百炼账号，无需额外 key）；`ChatCompletionClient` 抽象，切 DeepSeek 仅改 `rag.qwen.base-url`/`model`/`api-key` 配置。浏览器用 `EventSource` 测；事件 `products`→`answer`*→`done`/`error`。
- **AI 导购 Agent（模块十六）**：`GET /api/assistant/chat?q=...`（SSE 流式，**需登录**）。手写多轮 Agent 循环，6 个工具（4 只读 + 2 个人数据），事件 `step/tool/products/answer/done/error`。同样复用 `DASHSCOPE_API_KEY`（qwen tools API）。Resilience4j CB `aiLlm`/`aiEmbedding` 已配；embedding 失败自动降级关键词搜索。`/actuator/prometheus` 可查 `ai.*` 指标（需 ADMIN token）。

## Common Commands

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Test
mvn test
```
