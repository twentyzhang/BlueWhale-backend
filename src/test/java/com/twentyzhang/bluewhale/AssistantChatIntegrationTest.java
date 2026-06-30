package com.twentyzhang.bluewhale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyzhang.bluewhale.service.AgentChatClient;
import com.twentyzhang.bluewhale.service.EmbeddingClient;
import com.twentyzhang.bluewhale.service.llm.AgentTurn;
import com.twentyzhang.bluewhale.service.llm.ToolCall;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 导购 Agent 端到端集成测试（桩 AgentChatClient，真实 ToolRegistry / 工具 / 控制器）。
 *
 * <p>验证两个场景：
 * <ol>
 *   <li>未登录访问 → HTTP 401（由 Spring Security 拦截）。</li>
 *   <li>已登录 → SSE 事件流至少含 {@code step}、{@code products}、{@code answer}、{@code done} 事件，
 *       且流式片段 "推荐"/"这款" 出现在响应体中。</li>
 * </ol>
 *
 * <p>基础设施依赖：
 * <ul>
 *   <li>Spring 上下文启动需要 MySQL（Flyway 自动迁移），这是既有集成测试的共同前提。</li>
 *   <li>认证场景额外需要 Redis（令牌版本校验）；通过 {@link #loginOrNull()} 探测，
 *       不可用时 {@link Assumptions#assumeTrue} 优雅跳过，不阻断纯单测套件。</li>
 *   <li>Qdrant 不可用时 {@code SemanticSearchServiceImpl} 内部捕获异常并降级关键词搜索，
 *       {@code search_products} 工具始终成功返回列表（可能为空），{@code products} 事件无条件发出；
 *       因此 {@code products} 断言无须基础设施守卫，不存在偶发失败。</li>
 * </ul>
 *
 * <p>注意：使用 {@code MockMvc}（非 {@code TestRestTemplate}）以避免 SSE 分块传输编码
 * （chunked transfer encoding）在真实 TCP 栈上引发的 EOF 解析问题。MockMvc 在进程内
 * 拦截响应，能正确收集 {@code SseEmitter} 异步写入的所有事件内容。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AI 导购 Agent 端到端集成测试")
class AssistantChatIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    /** 仅桩化 LLM 客户端；ToolRegistry / 工具 / 控制器均使用真实实现。 */
    @MockitoBean
    AgentChatClient agentChatClient;

    /**
     * 桩化 embedding 客户端（固定向量）。否则 search_products 工具会触发真实
     * {@code TongyiEmbeddingClient} → 真实 DashScope 网络调用：违反「集成测试绝不打真实 DashScope」，
     * 且 Task 9 的 {@code @Retry}（2 次 + 500ms）会把单次请求拉到 ~16s，超过下方 15s 异步等待导致超时。
     */
    @MockitoBean
    EmbeddingClient embeddingClient;

    private static float[] fixedVector() {
        float[] v = new float[1024];
        v[0] = 1.0f;
        return v;
    }

    // -----------------------------------------------------------------------
    // 场景 1：未登录
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("未登录访问 /api/assistant/chat → 401")
    void unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/assistant/chat").param("q", "耳机"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // 场景 2：已登录 → SSE 事件流
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("已登录 → SSE 事件流含 step / products / answer / done 及流式文本片段")
    void authenticated_streamsAgentEvents() throws Exception {
        // 桩脚本（两轮）：
        //   第 1 轮 chat() → 要求调 search_products（触发真实工具执行）
        //   第 2 轮 chat() → 收敛（hasToolCalls = false）→ 进入 streamFinal
        when(agentChatClient.chat(anyList(), anyList()))
                .thenReturn(new AgentTurn(null,
                        List.of(new ToolCall("c1", "search_products", "{\"q\":\"耳机\"}"))))
                .thenReturn(new AgentTurn("推荐这款", List.of()));

        // streamFinal：模拟流式产出两个 delta，Agent 循环将各发一次 answer 事件
        doAnswer(inv -> {
            Consumer<String> cb = inv.getArgument(1);
            cb.accept("推荐");
            cb.accept("这款");
            return null;
        }).when(agentChatClient).streamFinal(anyList(), any());

        // 桩 embedding：固定向量，search_products 走向量库/降级关键词搜索，绝不打真实 DashScope
        when(embeddingClient.embed(anyString())).thenReturn(fixedVector());

        // 基础设施守卫：尝试真实登录（需 MySQL + Redis）
        // loginOrNull() 任何异常 → null → assumeTrue 优雅跳过本测试
        String token = loginOrNull();
        Assumptions.assumeTrue(token != null,
                "基础设施（MySQL/Redis）不可用，跳过认证 SSE 集成测试");

        // 发出异步请求（SseEmitter 立即返回，后台线程推送事件）
        // SseEmitter 与 DeferredResult/Callable 不同：响应体在异步期间增量写入，
        // 无需 asyncDispatch（dispatch 会重入 Security 过滤链而丢失 SecurityContext）；
        // 直接等待 emitter.complete() 后从 MockHttpServletResponse 读取已累积的内容。
        MvcResult asyncMvcResult = mvc.perform(
                        get("/api/assistant/chat")
                                .param("q", "耳机")
                                .header("Authorization", "Bearer " + token)
                                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 等待后台线程完成整个 Agent 循环（emitter.complete() 触发 async 完成）
        asyncMvcResult.getAsyncResult(15_000L);

        // 断言 HTTP 状态
        assertThat(asyncMvcResult.getResponse().getStatus())
                .as("SSE 响应应为 200").isEqualTo(200);

        // 读取响应体（UTF-8）：MockMvc 在进程内拦截，SSE 事件已被增量写入 MockHttpServletResponse
        // 必须显式指定 UTF-8，否则默认 ISO-8859-1 会乱码中文字符
        String body = asyncMvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).as("SSE 流应含 step 事件（工具执行前）").contains("step");
        // search_products 的 SemanticSearchService 内部降级，execute() 从不抛出；
        // producesProducts()=true → products 事件无条件发出，断言无须基础设施守卫。
        assertThat(body).as("SSE 流应含 products 事件（工具命中商品列表）").contains("products");
        assertThat(body).as("SSE 流应含 answer 事件（流式文本）").contains("answer");
        assertThat(body).as("SSE 流应含 done 事件（流结束）").contains("done");
        // 确认 streamFinal 回调被真实触发：delta 出现在响应体中
        assertThat(body).as("响应体应含流式片段 '推荐'").contains("推荐");
        assertThat(body).as("响应体应含流式片段 '这款'").contains("这款");
    }

    // -----------------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------------

    /**
     * 尝试注册（首次运行若账号不存在则写入 user 表）后以种子管理员账号登录，返回 access token；
     * 任何异常（连接失败、HTTP 4xx/5xx 等）统一返回 null，供 assumeTrue 守卫使用。
     * 同时验证 MySQL（注册/登录写入）和 Redis（令牌版本写入）均可用。
     * 注意：账号已存在时注册接口预期返回 4xx，忽略该错误继续登录。
     */
    private String loginOrNull() {
        try {
            // 注册（已存在则忽略）
            mvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"phone\":\"13000000000\",\"password\":\"Admin@123456\"," +
                             "\"nickname\":\"管理员\",\"role\":\"ADMIN\"}"));
        } catch (Exception ignored) {
            // 已注册 / 注册路径异常都不致命，继续尝试登录
        }
        try {
            String body = mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"13000000000\",\"password\":\"Admin@123456\"}"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            JsonNode data = objectMapper.readTree(body).path("data");
            if (data.isMissingNode() || data.isNull()) return null;
            JsonNode tokenNode = data.get("token");
            return tokenNode != null && !tokenNode.isNull() ? tokenNode.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
