package com.twentyzhang.bluewhale.service;

/** 文本向量化（可插拔：现通义，未来可换厂商）。 */
public interface EmbeddingClient {

    /** 把文本编码为定长向量（维度 == 配置 vector-size）。 */
    float[] embed(String text);
}
