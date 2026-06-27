package com.twentyzhang.bluewhale.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    private final Qdrant qdrant = new Qdrant();
    private final Tongyi tongyi = new Tongyi();
    private final Outbox outbox = new Outbox();
    private final Semantic semantic = new Semantic();

    @Data
    public static class Qdrant {
        private String url = "http://localhost:6333";
        private String apiKey = "";
        private String collection = "products";
        private int vectorSize = 1024;
        private String distance = "Cosine";
    }

    @Data
    public static class Tongyi {
        private String apiKey = "";
        private String embeddingUrl =
                "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
        private String embeddingModel = "text-embedding-v3";
        private int embeddingDimension = 1024;
    }

    @Data
    public static class Outbox {
        private int pollBatchSize = 50;
        private long pollDelayMs = 5000;
        private int maxRetry = 5;
    }

    @Data
    public static class Semantic {
        private int defaultTopK = 50;
    }
}
