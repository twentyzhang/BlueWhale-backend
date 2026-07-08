package com.twentyzhang.bluewhale.config;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 自定义 Micrometer 指标：AI Agent 工具调用、轮次、限流拒绝。
 * <p>
 * 指标名称：
 * <ul>
 *   <li>{@code ai.tool.invocations} — 工具调用计数，tags: tool, ok(true/false)</li>
 *   <li>{@code ai.agent.rounds}     — 每次 Agent 请求的 LLM 调用轮次分布摘要</li>
 *   <li>{@code ai.ratelimit.rejected} — 限流拒绝计数</li>
 * </ul>
 */
@Component
public class AiMetrics {

    private final MeterRegistry registry;
    private final DistributionSummary rounds;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.rounds = DistributionSummary.builder("ai.agent.rounds")
                .description("每次 Agent 请求的 LLM 调用轮次")
                .register(registry);
    }

    /** 记录一次工具调用，{@code ok=true} 表示成功，{@code ok=false} 表示异常。 */
    public void recordToolInvocation(String tool, boolean ok) {
        registry.counter("ai.tool.invocations", "tool", tool, "ok", String.valueOf(ok)).increment();
    }

    /** 记录本次 Agent 请求共使用了多少轮 LLM 调用（收敛或超上限时各记一次）。 */
    public void recordAgentRounds(int n) {
        rounds.record(n);
    }

    /** 记录一次限流拒绝。 */
    public void recordRateLimitRejected() {
        registry.counter("ai.ratelimit.rejected").increment();
    }
}
