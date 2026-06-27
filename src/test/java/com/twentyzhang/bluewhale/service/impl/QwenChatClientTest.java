package com.twentyzhang.bluewhale.service.impl;

import com.twentyzhang.bluewhale.config.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QwenChatClient SSE 解析")
class QwenChatClientTest {

    private final QwenChatClient client = new QwenChatClient(new RagProperties(), new ObjectMapper());

    @Test
    @DisplayName("extractDelta：取 choices[0].delta.content")
    void extractDelta_picksContent() {
        assertEquals("你好",
                client.extractDelta("{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}"));
    }

    @Test
    @DisplayName("extractDelta：无 content / 非法 JSON 返回 null")
    void extractDelta_missingOrBad_returnsNull() {
        assertNull(client.extractDelta("{\"choices\":[{\"delta\":{}}]}"));
        assertNull(client.extractDelta("not-json"));
    }

    @Test
    @DisplayName("consumeStream：逐行取 delta，[DONE] 终止，后续忽略")
    void consumeStream_collectsDeltasUntilDone() throws Exception {
        String sse = String.join("\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}",
                "",
                "data: [DONE]",
                "data: {\"choices\":[{\"delta\":{\"content\":\"忽略\"}}]}");

        List<String> out = new ArrayList<>();
        client.consumeStream(new BufferedReader(new StringReader(sse)), out::add);

        assertEquals(List.of("你", "好"), out);
    }
}
