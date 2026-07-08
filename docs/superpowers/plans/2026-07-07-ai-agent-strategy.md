# AI Agent Strategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build AI-4: a lightweight strategy layer for `/api/assistant/chat` so the Agent can classify user intent, ask one clarification question for vague requests, and inject intent-specific guidance into the existing tools loop.

**Architecture:** Add a small `service.agent` package containing pure Java strategy components. `AssistantAgentServiceImpl` will call these components before the existing LLM tools loop; if clarification is needed it emits `answer` and `done` immediately, otherwise it composes an intent-aware system prompt and keeps the existing `AgentChatClient` / `ToolRegistry` flow.

**Tech Stack:** Java 17, Spring Boot 3.4.0, JUnit 5, Mockito, AssertJ, Spring MVC `SseEmitter`, existing `AgentChatClient` and `Tool` abstractions.

## Global Constraints

- Keep endpoint unchanged: `GET /api/assistant/chat`.
- Keep login requirement unchanged.
- Keep Agent tools read-only; do not add write tools.
- Do not add Spring AI, LangChain4j, or new LLM dependencies.
- Do not add long-term conversation memory, user profile persistence, or new database tables.
- Keep SSE protocol compatible: no new event type; clarification uses `answer` then `done`.
- Existing normal events remain `step/tool/products/answer/done/error`.
- Prefer pure Java strategy code with deterministic unit tests.
- Follow existing project convention: `BusinessException`/SSE behavior stays unchanged; no HTTP contract break.

---

## File Structure

Create:

- `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentIntent.java`  
  Intent enum used by classifier, clarification policy, and prompt composer.
- `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentIntentClassifier.java`  
  Rule-based classifier from user text to `AgentIntent`.
- `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentClarificationPolicy.java`  
  Returns an optional one-question clarification for clearly vague requests.
- `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentPromptComposer.java`  
  Builds final system prompt from `AgentProperties.systemPrompt` and intent-specific hint.
- `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentIntentClassifierTest.java`
- `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentClarificationPolicyTest.java`
- `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentPromptComposerTest.java`

Modify:

- `src/main/java/com/twentyzhang/bluewhale/service/impl/AssistantAgentServiceImpl.java`  
  Inject strategy components and branch before the LLM loop.
- `src/main/java/com/twentyzhang/bluewhale/config/AgentProperties.java`  
  Strengthen default base system prompt.
- `src/main/java/com/twentyzhang/bluewhale/service/tool/SearchProductsTool.java`
- `src/main/java/com/twentyzhang/bluewhale/service/tool/ProductDetailTool.java`
- `src/main/java/com/twentyzhang/bluewhale/service/tool/CheckStockTool.java`
- `src/main/java/com/twentyzhang/bluewhale/service/tool/ListClaimableCouponsTool.java`
- `src/main/java/com/twentyzhang/bluewhale/service/tool/MyOrdersTool.java`
- `src/main/java/com/twentyzhang/bluewhale/service/tool/MyCouponsTool.java`
- `src/test/java/com/twentyzhang/bluewhale/service/AssistantAgentServiceTest.java`
- `src/test/java/com/twentyzhang/bluewhale/AssistantChatIntegrationTest.java`
- Documentation files in Task 6.

---

### Task 1: Strategy Components

**Files:**
- Create: `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentIntent.java`
- Create: `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentIntentClassifier.java`
- Create: `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentClarificationPolicy.java`
- Test: `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentIntentClassifierTest.java`
- Test: `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentClarificationPolicyTest.java`

**Interfaces:**
- Produces: `AgentIntent`
- Produces: `AgentIntentClassifier.classify(String q): AgentIntent`
- Produces: `AgentClarificationPolicy.maybeAsk(String q, AgentIntent intent): Optional<String>`
- Consumed by: Task 2 `AssistantAgentServiceImpl`

- [ ] **Step 1: Write failing classifier tests**

Create `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentIntentClassifierTest.java`:

```java
package com.twentyzhang.bluewhale.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentIntentClassifierTest {

    private final AgentIntentClassifier classifier = new AgentIntentClassifier();

    @Test
    void vagueRecommendation_isUnclear() {
        assertThat(classifier.classify("推荐点东西")).isEqualTo(AgentIntent.UNCLEAR);
        assertThat(classifier.classify("买什么好")).isEqualTo(AgentIntent.UNCLEAR);
    }

    @Test
    void concreteRecommendation_isProductRecommendation() {
        assertThat(classifier.classify("送长辈的健康礼物，预算100"))
                .isEqualTo(AgentIntent.PRODUCT_RECOMMENDATION);
        assertThat(classifier.classify("夏天喝点无糖的"))
                .isEqualTo(AgentIntent.PRODUCT_RECOMMENDATION);
    }

    @Test
    void personalQuestions_areClassifiedFirst() {
        assertThat(classifier.classify("我的订单到哪了")).isEqualTo(AgentIntent.PERSONAL_ORDER);
        assertThat(classifier.classify("我上次买过什么")).isEqualTo(AgentIntent.PERSONAL_ORDER);
        assertThat(classifier.classify("我有哪些券能用")).isEqualTo(AgentIntent.PERSONAL_COUPON);
    }

    @Test
    void stockOrDetailQuestions_areClassified() {
        assertThat(classifier.classify("这个商品还有货吗")).isEqualTo(AgentIntent.STOCK_OR_DETAIL);
        assertThat(classifier.classify("这个多少钱")).isEqualTo(AgentIntent.STOCK_OR_DETAIL);
    }

    @Test
    void unknownText_fallsBackToGeneralGuidance() {
        assertThat(classifier.classify("你好")).isEqualTo(AgentIntent.GENERAL_GUIDANCE);
        assertThat(classifier.classify(null)).isEqualTo(AgentIntent.GENERAL_GUIDANCE);
    }
}
```

- [ ] **Step 2: Write failing clarification tests**

Create `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentClarificationPolicyTest.java`:

```java
package com.twentyzhang.bluewhale.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentClarificationPolicyTest {

    private final AgentClarificationPolicy policy = new AgentClarificationPolicy();

    @Test
    void unclearIntent_returnsOneQuestion() {
        assertThat(policy.maybeAsk("推荐点东西", AgentIntent.UNCLEAR))
                .hasValue("你是想自己吃/用，还是送人？如果送人，大概预算是多少？");
    }

    @Test
    void concreteRecommendation_doesNotAsk() {
        assertThat(policy.maybeAsk("送长辈的健康礼物，预算100", AgentIntent.PRODUCT_RECOMMENDATION))
                .isEmpty();
        assertThat(policy.maybeAsk("夏天喝点无糖的", AgentIntent.PRODUCT_RECOMMENDATION))
                .isEmpty();
    }

    @Test
    void personalIntent_doesNotAsk() {
        assertThat(policy.maybeAsk("我的订单到哪了", AgentIntent.PERSONAL_ORDER)).isEmpty();
        assertThat(policy.maybeAsk("我的优惠券", AgentIntent.PERSONAL_COUPON)).isEmpty();
    }
}
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```bash
mvn "-Dtest=AgentIntentClassifierTest,AgentClarificationPolicyTest" test
```

Expected: compilation fails because `AgentIntent`, `AgentIntentClassifier`, and `AgentClarificationPolicy` do not exist.

- [ ] **Step 4: Implement `AgentIntent`**

Create `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentIntent.java`:

```java
package com.twentyzhang.bluewhale.service.agent;

public enum AgentIntent {
    PRODUCT_RECOMMENDATION,
    PERSONAL_ORDER,
    PERSONAL_COUPON,
    STOCK_OR_DETAIL,
    GENERAL_GUIDANCE,
    UNCLEAR
}
```

- [ ] **Step 5: Implement `AgentIntentClassifier`**

Create `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentIntentClassifier.java`:

```java
package com.twentyzhang.bluewhale.service.agent;

import org.springframework.stereotype.Component;

@Component
public class AgentIntentClassifier {

    public AgentIntent classify(String q) {
        String text = normalize(q);
        if (text.isBlank()) return AgentIntent.GENERAL_GUIDANCE;

        if (containsAny(text, "我的订单", "订单", "物流", "到哪", "买过", "上次买")) {
            return AgentIntent.PERSONAL_ORDER;
        }
        if (containsAny(text, "我的券", "优惠券", "券", "优惠", "能用吗")) {
            return AgentIntent.PERSONAL_COUPON;
        }
        if (containsAny(text, "库存", "有货", "还有吗", "还有货", "价格", "多少钱", "详情")) {
            return AgentIntent.STOCK_OR_DETAIL;
        }
        if (isBareRecommendation(text)) {
            return AgentIntent.UNCLEAR;
        }
        if (containsAny(text, "推荐", "送", "适合", "预算", "想买", "夏天", "长辈", "礼物", "无糖", "健康", "饮料", "做饭")) {
            return AgentIntent.PRODUCT_RECOMMENDATION;
        }
        return AgentIntent.GENERAL_GUIDANCE;
    }

    private static String normalize(String q) {
        return q == null ? "" : q.trim().replace(" ", "");
    }

    private static boolean isBareRecommendation(String text) {
        return containsAny(text, "推荐点东西", "买什么好", "有什么推荐")
                && !containsAny(text, "送", "预算", "元", "块", "夏天", "长辈", "礼物", "无糖", "饮料", "做饭", "健康");
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}
```

- [ ] **Step 6: Implement `AgentClarificationPolicy`**

Create `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentClarificationPolicy.java`:

```java
package com.twentyzhang.bluewhale.service.agent;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AgentClarificationPolicy {

    static final String DEFAULT_QUESTION = "你是想自己吃/用，还是送人？如果送人，大概预算是多少？";

    public Optional<String> maybeAsk(String q, AgentIntent intent) {
        if (intent != AgentIntent.UNCLEAR) {
            return Optional.empty();
        }
        return Optional.of(DEFAULT_QUESTION);
    }
}
```

- [ ] **Step 7: Run strategy tests**

Run:

```bash
mvn "-Dtest=AgentIntentClassifierTest,AgentClarificationPolicyTest" test
```

Expected: tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/service/agent/AgentIntent.java src/main/java/com/twentyzhang/bluewhale/service/agent/AgentIntentClassifier.java src/main/java/com/twentyzhang/bluewhale/service/agent/AgentClarificationPolicy.java src/test/java/com/twentyzhang/bluewhale/service/agent/AgentIntentClassifierTest.java src/test/java/com/twentyzhang/bluewhale/service/agent/AgentClarificationPolicyTest.java
git commit -m "feat: add agent intent strategy"
```

---

### Task 2: Prompt Composer

**Files:**
- Create: `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentPromptComposer.java`
- Test: `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentPromptComposerTest.java`

**Interfaces:**
- Consumes: `AgentProperties.getSystemPrompt(): String`
- Consumes: `AgentIntent`
- Produces: `AgentPromptComposer.compose(AgentIntent intent): String`
- Consumed by: Task 3 `AssistantAgentServiceImpl`

- [ ] **Step 1: Write failing prompt composer tests**

Create `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentPromptComposerTest.java`:

```java
package com.twentyzhang.bluewhale.service.agent;

import com.twentyzhang.bluewhale.config.AgentProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPromptComposerTest {

    @Test
    void recommendationPrompt_containsToolGuidanceAndLimits() {
        AgentProperties props = new AgentProperties();
        props.setSystemPrompt("基础提示");
        AgentPromptComposer composer = new AgentPromptComposer(props);

        String prompt = composer.compose(AgentIntent.PRODUCT_RECOMMENDATION);

        assertThat(prompt).contains("基础提示");
        assertThat(prompt).contains("优先调用 search_products");
        assertThat(prompt).contains("最多推荐 3 个商品");
        assertThat(prompt).contains("结论先行");
    }

    @Test
    void personalCouponPrompt_prioritizesMyCouponsTool() {
        AgentProperties props = new AgentProperties();
        props.setSystemPrompt("基础提示");
        AgentPromptComposer composer = new AgentPromptComposer(props);

        String prompt = composer.compose(AgentIntent.PERSONAL_COUPON);

        assertThat(prompt).contains("优先调用 list_my_coupons");
        assertThat(prompt).contains("当前登录用户");
    }

    @Test
    void personalOrderPrompt_prioritizesMyOrdersTool() {
        AgentProperties props = new AgentProperties();
        props.setSystemPrompt("基础提示");
        AgentPromptComposer composer = new AgentPromptComposer(props);

        String prompt = composer.compose(AgentIntent.PERSONAL_ORDER);

        assertThat(prompt).contains("优先调用 get_my_orders");
        assertThat(prompt).contains("不要猜测订单状态");
    }
}
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
mvn "-Dtest=AgentPromptComposerTest" test
```

Expected: compilation fails because `AgentPromptComposer` does not exist.

- [ ] **Step 3: Implement `AgentPromptComposer`**

Create `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentPromptComposer.java`:

```java
package com.twentyzhang.bluewhale.service.agent;

import com.twentyzhang.bluewhale.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentPromptComposer {

    private final AgentProperties props;

    public String compose(AgentIntent intent) {
        AgentIntent safeIntent = intent == null ? AgentIntent.GENERAL_GUIDANCE : intent;
        return props.getSystemPrompt()
                + "\n\n综合型导购规则："
                + "\n- 只能基于工具返回的真实数据回答，不得编造商品、库存、订单或优惠券。"
                + "\n- 信息不足时只追问一个关键问题。"
                + "\n- 推荐时最多推荐 3 个商品，结论先行，再说明理由、价格/库存/优惠和下一步建议。"
                + "\n- 工具结果为空或失败时如实说明，并给出用户可以补充的条件。"
                + "\n\n当前意图：" + safeIntent.name()
                + "\n" + hint(safeIntent);
    }

    private static String hint(AgentIntent intent) {
        return switch (intent) {
            case PRODUCT_RECOMMENDATION ->
                    "推荐场景：优先调用 search_products；必要时再调用 get_product_detail、check_stock、list_claimable_coupons。";
            case PERSONAL_ORDER ->
                    "订单场景：优先调用 get_my_orders；只能总结当前登录用户的订单，不要猜测订单状态。";
            case PERSONAL_COUPON ->
                    "优惠券场景：优先调用 list_my_coupons 查询当前登录用户已领取的券；必要时再调用 list_claimable_coupons 查询可领券。";
            case STOCK_OR_DETAIL ->
                    "库存/详情场景：如果用户没有给 productId，先用 search_products 或 get_product_detail 定位商品，再调用 check_stock。";
            case UNCLEAR ->
                    "模糊场景：如果仍进入模型循环，请先用简短中文追问一个关键条件。";
            case GENERAL_GUIDANCE ->
                    "一般导购场景：可以先调用 search_products 获取真实商品，再给保守建议。";
        };
    }
}
```

- [ ] **Step 4: Run prompt composer tests**

Run:

```bash
mvn "-Dtest=AgentPromptComposerTest" test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/service/agent/AgentPromptComposer.java src/test/java/com/twentyzhang/bluewhale/service/agent/AgentPromptComposerTest.java
git commit -m "feat: compose agent prompts by intent"
```

---

### Task 3: Wire Strategy Into Assistant Agent Service

**Files:**
- Modify: `src/main/java/com/twentyzhang/bluewhale/service/impl/AssistantAgentServiceImpl.java`
- Modify test helper constructor in: `src/test/java/com/twentyzhang/bluewhale/service/AssistantAgentServiceTest.java`

**Interfaces:**
- Consumes: `AgentIntentClassifier.classify(String)`
- Consumes: `AgentClarificationPolicy.maybeAsk(String, AgentIntent)`
- Consumes: `AgentPromptComposer.compose(AgentIntent)`
- Produces: unchanged `AssistantAgentService.chat(String, AgentContext, SseEmitter): void`

- [ ] **Step 1: Add failing service test for clarification short-circuit**

Append to `AssistantAgentServiceTest`:

```java
@Test
void vagueRequest_asksClarificationWithoutCallingLlmOrTools() throws Exception {
    ToolRegistry reg = new ToolRegistry(List.of(fakeSearch()));

    service(reg).chat("推荐点东西", new AgentContext(1L, "CUSTOMER"), emitter);

    verify(client, never()).chat(anyList(), anyList());
    verify(client, never()).streamFinal(anyList(), any());
    verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    verify(emitter).complete();
}
```

Expected behavior before implementation: test fails because current service always calls `client.chat`.

- [ ] **Step 2: Update test service factory to pass strategy components**

Modify `AssistantAgentServiceTest.service(ToolRegistry reg)`:

```java
private AssistantAgentServiceImpl service(ToolRegistry reg) {
    AgentProperties props = new AgentProperties();
    return new AssistantAgentServiceImpl(
            client,
            reg,
            props,
            new ObjectMapper(),
            Runnable::run,
            new AiMetrics(new SimpleMeterRegistry()),
            new com.twentyzhang.bluewhale.service.agent.AgentIntentClassifier(),
            new com.twentyzhang.bluewhale.service.agent.AgentClarificationPolicy(),
            new com.twentyzhang.bluewhale.service.agent.AgentPromptComposer(props));
}
```

Also update direct constructor calls in existing tests to pass the same three strategy objects. Use this local helper pattern:

```java
private AssistantAgentServiceImpl service(ToolRegistry reg, AgentProperties props) {
    return new AssistantAgentServiceImpl(
            client,
            reg,
            props,
            new ObjectMapper(),
            Runnable::run,
            new AiMetrics(new SimpleMeterRegistry()),
            new com.twentyzhang.bluewhale.service.agent.AgentIntentClassifier(),
            new com.twentyzhang.bluewhale.service.agent.AgentClarificationPolicy(),
            new com.twentyzhang.bluewhale.service.agent.AgentPromptComposer(props));
}
```

- [ ] **Step 3: Run service test and verify compile failure**

Run:

```bash
mvn "-Dtest=AssistantAgentServiceTest#vagueRequest_asksClarificationWithoutCallingLlmOrTools" test
```

Expected: compilation fails because `AssistantAgentServiceImpl` constructor does not accept the new strategy components.

- [ ] **Step 4: Modify `AssistantAgentServiceImpl` constructor and fields**

Add imports:

```java
import com.twentyzhang.bluewhale.service.agent.AgentClarificationPolicy;
import com.twentyzhang.bluewhale.service.agent.AgentIntent;
import com.twentyzhang.bluewhale.service.agent.AgentIntentClassifier;
import com.twentyzhang.bluewhale.service.agent.AgentPromptComposer;
```

Add fields:

```java
private final AgentIntentClassifier intentClassifier;
private final AgentClarificationPolicy clarificationPolicy;
private final AgentPromptComposer promptComposer;
```

Replace constructor with:

```java
public AssistantAgentServiceImpl(AgentChatClient client, ToolRegistry registry,
                                 AgentProperties props, ObjectMapper om,
                                 @Qualifier("assistantStreamExecutor") Executor executor,
                                 AiMetrics metrics,
                                 AgentIntentClassifier intentClassifier,
                                 AgentClarificationPolicy clarificationPolicy,
                                 AgentPromptComposer promptComposer) {
    this.client = client;
    this.registry = registry;
    this.props = props;
    this.om = om;
    this.executor = executor;
    this.metrics = metrics;
    this.intentClassifier = intentClassifier;
    this.clarificationPolicy = clarificationPolicy;
    this.promptComposer = promptComposer;
}
```

- [ ] **Step 5: Add strategy branch in `runLoop`**

In `runLoop`, replace:

```java
List<AgentMessage> messages = new ArrayList<>();
messages.add(AgentMessage.system(props.getSystemPrompt()));
messages.add(AgentMessage.user(q));
List<Map<String, Object>> schemas = registry.toolSchemas();

try {
```

with:

```java
List<AgentMessage> messages = new ArrayList<>();
AgentIntent intent = intentClassifier.classify(q);
try {
    var clarification = clarificationPolicy.maybeAsk(q, intent);
    if (clarification.isPresent()) {
        send(emitter, "answer", clarification.get(), disconnected);
        send(emitter, "done", "", disconnected);
        metrics.recordAgentRounds(0);
        emitter.complete();
        return;
    }

    messages.add(AgentMessage.system(promptComposer.compose(intent)));
    messages.add(AgentMessage.user(q));
    List<Map<String, Object>> schemas = registry.toolSchemas();
```

This preserves the existing loop body inside the same `try`.

- [ ] **Step 6: Run focused service tests**

Run:

```bash
mvn "-Dtest=AssistantAgentServiceTest" test
```

Expected: all `AssistantAgentServiceTest` tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/service/impl/AssistantAgentServiceImpl.java src/test/java/com/twentyzhang/bluewhale/service/AssistantAgentServiceTest.java
git commit -m "feat: route assistant chat through strategy layer"
```

---

### Task 4: Strengthen Prompt Defaults and Tool Descriptions

**Files:**
- Modify: `src/main/java/com/twentyzhang/bluewhale/config/AgentProperties.java`
- Modify: `src/main/java/com/twentyzhang/bluewhale/service/tool/SearchProductsTool.java`
- Modify: `src/main/java/com/twentyzhang/bluewhale/service/tool/ProductDetailTool.java`
- Modify: `src/main/java/com/twentyzhang/bluewhale/service/tool/CheckStockTool.java`
- Modify: `src/main/java/com/twentyzhang/bluewhale/service/tool/ListClaimableCouponsTool.java`
- Modify: `src/main/java/com/twentyzhang/bluewhale/service/tool/MyOrdersTool.java`
- Modify: `src/main/java/com/twentyzhang/bluewhale/service/tool/MyCouponsTool.java`
- Test: existing `ToolRegistryTest`, `PublicToolsTest`, `PersonalToolsTest`, `AgentPromptComposerTest`

**Interfaces:**
- Produces: same `Tool.description(): String`
- Produces: same `AgentProperties.getSystemPrompt(): String`
- No API signature changes.

- [ ] **Step 1: Add assertions to prompt composer test**

In `AgentPromptComposerTest.recommendationPrompt_containsToolGuidanceAndLimits`, add:

```java
assertThat(prompt).contains("不得编造商品");
assertThat(prompt).contains("工具结果为空");
```

- [ ] **Step 2: Run relevant tests to see current failures**

Run:

```bash
mvn "-Dtest=AgentPromptComposerTest,PublicToolsTest,PersonalToolsTest" test
```

Expected: prompt assertions may fail until default prompt and descriptions are strengthened.

- [ ] **Step 3: Replace default `systemPrompt` in `AgentProperties`**

Use this exact text, keeping Java string concatenation readable:

```java
private String systemPrompt =
    "你是南鲸商城的综合型导购助手。只能依据工具返回的真实数据回答，不得编造商品、库存、订单或优惠券信息。" +
    "你可以帮助用户做商品推荐、比较商品、查询库存、查看可领优惠、查看我的订单和我的优惠券。" +
    "信息不足时先追问一个关键问题；信息足够时调用合适工具并用简洁中文回答。" +
    "推荐时最多推荐 3 个商品，先给结论，再说明理由、价格/库存/优惠提示和下一步建议。" +
    "找不到结果或工具失败时要如实说明，并建议用户补充预算、用途、对象或品类。";
```

- [ ] **Step 4: Update tool descriptions**

Use these descriptions:

`SearchProductsTool.description()`:

```java
return "按自然语言语义搜索商品。用于商品推荐、场景导购、预算/用途/对象类问题，例如送礼、夏天饮料、做饭调味、无糖食品。返回商品列表。";
```

`ProductDetailTool.description()`:

```java
return "查询单个商品详情，包括名称、价格、库存、评分等。用户提到具体商品并需要价格、评分、详情或进一步比较时使用。需 productId。";
```

`CheckStockTool.description()`:

```java
return "查询某商品当前库存数量。用户问还有货吗、库存多少、能不能买时使用。需先知道 productId。";
```

`ListClaimableCouponsTool.description()`:

```java
return "查询当前可领取的优惠券列表。用户问平台或店铺有什么券、有什么优惠可以领时使用。";
```

`MyOrdersTool.description()`:

```java
return "查询当前登录用户自己的订单、状态和物流信息。用户问我的订单、物流、上次买过什么时使用。必须只基于当前用户上下文。可选 status 过滤。";
```

`MyCouponsTool.description()`:

```java
return "查询当前登录用户已领取的优惠券。用户问我的券、我有哪些优惠、我的券能不能用时使用。必须只基于当前用户上下文。可选 status（UNUSED/USED/EXPIRED）过滤。";
```

- [ ] **Step 5: Run relevant tests**

Run:

```bash
mvn "-Dtest=AgentPromptComposerTest,PublicToolsTest,PersonalToolsTest" test
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/twentyzhang/bluewhale/config/AgentProperties.java src/main/java/com/twentyzhang/bluewhale/service/tool/SearchProductsTool.java src/main/java/com/twentyzhang/bluewhale/service/tool/ProductDetailTool.java src/main/java/com/twentyzhang/bluewhale/service/tool/CheckStockTool.java src/main/java/com/twentyzhang/bluewhale/service/tool/ListClaimableCouponsTool.java src/main/java/com/twentyzhang/bluewhale/service/tool/MyOrdersTool.java src/main/java/com/twentyzhang/bluewhale/service/tool/MyCouponsTool.java src/test/java/com/twentyzhang/bluewhale/service/agent/AgentPromptComposerTest.java
git commit -m "feat: strengthen assistant prompts and tools"
```

---

### Task 5: Integration Coverage for Clarification and Intent Hints

**Files:**
- Modify: `src/test/java/com/twentyzhang/bluewhale/service/AssistantAgentServiceTest.java`
- Modify: `src/test/java/com/twentyzhang/bluewhale/AssistantChatIntegrationTest.java`

**Interfaces:**
- Consumes: strategy-wired `AssistantAgentServiceImpl`
- Produces: tests proving SSE compatibility and prompt hints.

- [ ] **Step 1: Add service test for prompt intent hint**

Append to `AssistantAgentServiceTest`:

```java
@Test
void couponQuestion_addsCouponIntentHintToSystemPrompt() throws Exception {
    ToolRegistry reg = new ToolRegistry(List.of(fakeSearch()));
    when(client.chat(anyList(), anyList()))
            .thenReturn(new AgentTurn("ok", List.of()));
    doAnswer(inv -> null).when(client).streamFinal(anyList(), any());

    service(reg).chat("我的优惠券有哪些能用", new AgentContext(1L, "CUSTOMER"), emitter);

    verify(client).chat(argThat(messages -> {
        AgentMessage system = (AgentMessage) messages.get(0);
        return system.content().contains("PERSONAL_COUPON")
                && system.content().contains("优先调用 list_my_coupons");
    }), anyList());
}
```

- [ ] **Step 2: Add integration test for clarification SSE**

Append to `AssistantChatIntegrationTest`:

```java
@Test
@DisplayName("模糊推荐输入 -> 直接追问，不产生 step/tool/products")
void vagueRecommendation_streamsClarificationOnly() throws Exception {
    String token = loginOrNull();
    Assumptions.assumeTrue(token != null,
            "基础设施（MySQL/Redis）不可用，跳过认证 SSE 集成测试");

    MvcResult asyncMvcResult = mvc.perform(
                    get("/api/assistant/chat")
                            .param("q", "推荐点东西")
                            .header("Authorization", "Bearer " + token)
                            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andReturn();

    asyncMvcResult.getAsyncResult(15_000L);

    String body = asyncMvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).contains("answer");
    assertThat(body).contains("done");
    assertThat(body).contains("预算");
    assertThat(body).doesNotContain("step");
    assertThat(body).doesNotContain("tool");
    assertThat(body).doesNotContain("products");
}
```

- [ ] **Step 3: Make existing integration test query explicitly recommendation-shaped**

In `authenticated_streamsAgentEvents`, change the request query to:

```java
.param("q", "推荐一款耳机")
```

This keeps the test aligned with the non-clarification path.

- [ ] **Step 4: Run focused tests**

Run:

```bash
mvn "-Dtest=AssistantAgentServiceTest,AssistantChatIntegrationTest" test
```

Expected: tests pass. If `AssistantChatIntegrationTest` skips due to missing MySQL/Redis, note the skip and run all non-integration focused tests.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/twentyzhang/bluewhale/service/AssistantAgentServiceTest.java src/test/java/com/twentyzhang/bluewhale/AssistantChatIntegrationTest.java
git commit -m "test: cover assistant strategy behavior"
```

---

### Task 6: Documentation and Final Verification

**Files:**
- Modify: `docs/进度.md`
- Modify: `docs/实现说明.md`
- Modify: `docs/重要决策说明.md`
- Modify: `docs/下一阶段路线图.md`
- Modify: `docs/api-spec.md`
- Modify: `docs/frontend-handoff.md`
- Modify: `docs/项目说明.md` if test count or project overview changes.

**Interfaces:**
- Consumes: implemented AI-4 behavior.
- Produces: updated project documentation.

- [ ] **Step 1: Update `docs/进度.md`**

Add an AI-4 section after the 2026-07-06 AI optimization note:

```markdown
## 2026-07-07 更新：AI-4 综合型导购 Agent 策略层

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| 意图分类 | 完成 | `AgentIntentClassifier` 用规则识别推荐、订单、优惠券、库存/详情、模糊需求 |
| 追问策略 | 完成 | 明显模糊输入直接推 `answer/done` 追问，不调用 LLM 和工具 |
| Prompt Composer | 完成 | 按 intent 组合 system prompt，强化工具选择与 grounding |
| SSE 兼容 | 完成 | 不新增事件；追问使用 `answer` + `done` |
```

- [ ] **Step 2: Update `docs/实现说明.md`**

Append under the AI Agent section:

```markdown
### AI-4：综合型导购 Agent 策略层

`AssistantAgentServiceImpl` 在进入 LLM tools 循环前先执行轻量策略层：

1. `AgentIntentClassifier.classify(q)` 将用户输入分为商品推荐、个人订单、个人优惠券、库存/详情、一般导购或模糊需求。
2. `AgentClarificationPolicy.maybeAsk(q, intent)` 对明显模糊问题返回一个追问文案。命中时直接推 SSE `answer` 和 `done`，不调用 LLM、不执行工具。
3. 非追问场景由 `AgentPromptComposer.compose(intent)` 生成 intent-aware system prompt，再进入 AI-3 既有 tools 循环。

该设计保持 `/api/assistant/chat` 和 SSE 协议不变：追问也是普通 `answer/done`，正常工具流仍是 `step/tool/products/answer/done`。
```

- [ ] **Step 3: Update `docs/重要决策说明.md`**

Append a new numbered decision after #65. Use the next number in the file:

```markdown
### 66. AI-4 综合型导购 Agent 采用轻量策略层而非长期记忆或框架

**决策**：在现有手写 Agent tools 循环前新增 `AgentIntentClassifier`、`AgentClarificationPolicy`、`AgentPromptComposer` 三个轻量组件，用规则完成意图分流、模糊需求追问和 intent-aware prompt 组合；不引入 Spring AI / LangChain4j，不新增长期会话记忆，不改变 SSE 协议。

**原因**：当前短板主要是单请求内的工具选择和追问稳定性，不是框架能力不足。规则策略层可测试、无 token 成本、不会破坏 AI-3 已完成的生产化护栏；长期记忆涉及存储、上下文裁剪和隐私边界，适合后续单独设计。
```

- [ ] **Step 4: Update API/front-end docs**

In `docs/api-spec.md` and `docs/frontend-handoff.md`, add a short note to the `/api/assistant/chat` section:

```markdown
AI-4 行为增强：当用户输入明显过于模糊（如“推荐点东西”）时，后端会直接通过 `answer` + `done` 返回一个追问，不产生 `step/tool/products` 事件；现有 SSE 事件协议不变，前端无需新增事件类型。
```

- [ ] **Step 5: Update route map**

In `docs/下一阶段路线图.md`, mark AI optimization mainline as AI-4 completed or in progress according to actual implementation status:

```markdown
| AI-4 | 综合型导购 Agent 策略层（意图分类 + 追问 + prompt composer） | ★★★☆☆ | ✅ 完成 | spec：`specs/2026-07-07-ai-agent-strategy-design.md`；plan：`plans/2026-07-07-ai-agent-strategy.md` |
```

- [ ] **Step 6: Run focused verification**

Run:

```bash
mvn "-Dtest=AgentIntentClassifierTest,AgentClarificationPolicyTest,AgentPromptComposerTest,AssistantAgentServiceTest,AssistantChatIntegrationTest" test
```

Expected: all focused tests pass, or `AssistantChatIntegrationTest` gracefully skips only when local MySQL/Redis are unavailable.

- [ ] **Step 7: Run full verification**

Run:

```bash
mvn test
```

Expected: full suite passes. If Redis/MySQL are unavailable, record the exact infrastructure failure and rerun focused non-infrastructure tests.

- [ ] **Step 8: Commit docs and final implementation state**

```bash
git add docs/进度.md docs/实现说明.md docs/重要决策说明.md docs/下一阶段路线图.md docs/api-spec.md docs/frontend-handoff.md docs/项目说明.md
git commit -m "docs: document agent strategy layer"
```

If code changes from previous tasks are already committed, this final commit should contain only documentation. If any test count changed in `docs/项目说明.md`, include that file; otherwise leave it untouched.

---

## Execution Notes

- Implement tasks in order. Task 3 depends on Tasks 1 and 2.
- Do not add database migrations.
- Do not add frontend requirements.
- Keep all new strategy tests pure unit tests.
- Preserve existing tool loop behavior for non-clarification requests.
- Use `apply_patch` for manual edits.
