package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.config.RagProperties;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.service.llm.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RagPromptBuilder")
class RagPromptBuilderTest {

    private static ProductListItemResponse p(String name) {
        return ProductListItemResponse.builder()
                .name(name).categoryName("饮料").price(new BigDecimal("9.90")).build();
    }

    @Test
    @DisplayName("返回 system+user 两条；system 含 grounding 约束")
    void build_systemAndUser() {
        RagProperties props = new RagProperties();
        RagPromptBuilder builder = new RagPromptBuilder(props);

        List<ChatMessage> msgs = builder.build("解渴的饮料", List.of(p("气泡水")));

        assertEquals(2, msgs.size());
        assertEquals("system", msgs.get(0).role());
        assertTrue(msgs.get(0).content().contains("只能"));
        assertTrue(msgs.get(0).content().contains("不得编造"));
        assertEquals("user", msgs.get(1).role());
        assertTrue(msgs.get(1).content().contains("解渴的饮料"));
        assertTrue(msgs.get(1).content().contains("气泡水"));
    }

    @Test
    @DisplayName("候选商品条数受 context-size 限制")
    void build_capsContextSize() {
        RagProperties props = new RagProperties();
        props.setContextSize(2);
        RagPromptBuilder builder = new RagPromptBuilder(props);

        String user = builder.build("x", List.of(p("A"), p("B"), p("C"))).get(1).content();

        assertTrue(user.contains("A"));
        assertTrue(user.contains("B"));
        assertFalse(user.contains("C"));
    }
}
