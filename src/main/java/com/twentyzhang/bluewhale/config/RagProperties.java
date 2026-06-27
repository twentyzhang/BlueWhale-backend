package com.twentyzhang.bluewhale.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private final Qwen qwen = new Qwen();
    private int topK = 6;
    private int contextSize = 6;
    private long timeoutMs = 30000;
    private long emitterTimeoutMs = 60000;

    @Data
    public static class Qwen {
        private String apiKey = "";
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String model = "qwen-plus";
        private double temperature = 0.3;
    }
}
