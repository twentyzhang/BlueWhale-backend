package com.twentyzhang.bluewhale.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** 一个可被 LLM 调用的工具（薄封装一个业务 Service 调用）。 */
public interface Tool {
    String name();
    String description();
    /** OpenAI function parameters 的 JSON Schema（object）。 */
    Map<String, Object> parametersSchema();
    /** 执行：args 为 LLM 传入参数，ctx 提供鉴权身份。返回可被 Jackson 序列化的结果。 */
    Object execute(JsonNode args, AgentContext ctx);
    /** 该工具的结果是否为商品卡列表（List&lt;ProductListItemResponse&gt;），用于额外推 products 事件。 */
    default boolean producesProducts() { return false; }
}
