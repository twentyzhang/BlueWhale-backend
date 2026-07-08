package com.twentyzhang.bluewhale.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiMetricsTest {

    @Test
    void recordsCountersAndSummary() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        AiMetrics m = new AiMetrics(reg);
        m.recordToolInvocation("search_products", true);
        m.recordToolInvocation("search_products", false);
        m.recordRateLimitRejected();
        m.recordAgentRounds(3);

        assertEquals(2.0, reg.get("ai.tool.invocations").counters().stream()
                .mapToDouble(c -> c.count()).sum());
        assertEquals(1.0, reg.get("ai.ratelimit.rejected").counter().count());
        assertEquals(1, reg.get("ai.agent.rounds").summary().count());
    }
}
