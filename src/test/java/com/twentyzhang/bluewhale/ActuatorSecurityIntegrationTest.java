package com.twentyzhang.bluewhale;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "management.metrics-token=test-metrics-token",
        "management.endpoint.prometheus.access=read_only",
        "management.health.redis.enabled=false"
})
@Import(ActuatorSecurityIntegrationTest.PrometheusTestConfig.class)
@DisplayName("Actuator security")
class ActuatorSecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("health remains public")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("prometheus accepts valid metrics token")
    void prometheusAcceptsMetricsToken() throws Exception {
        mvc.perform(get("/actuator/prometheus")
                        .header("X-Metrics-Token", "test-metrics-token"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("prometheus without token is not anonymous")
    void prometheusWithoutTokenRejected() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PrometheusTestConfig {

        @Bean
        PrometheusMeterRegistry prometheusMeterRegistry() {
            return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new io.prometheus.metrics.model.registry.PrometheusRegistry(), Clock.SYSTEM);
        }

        @Bean
        PrometheusScrapeEndpoint prometheusScrapeEndpoint(PrometheusMeterRegistry registry) {
            return new PrometheusScrapeEndpoint(registry.getPrometheusRegistry());
        }
    }
}
