package com.twentyzhang.bluewhale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "management.metrics-token=test-metrics-token",
        "management.prometheus.metrics.export.enabled=true",
        "management.health.redis.enabled=false"
})
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# HELP")))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("prometheus without token is not anonymous")
    void prometheusWithoutTokenRejected() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }
}
