package com.twentyzhang.bluewhale.service;

import com.twentyzhang.bluewhale.config.RagProperties;
import com.twentyzhang.bluewhale.dto.ProductListItemResponse;
import com.twentyzhang.bluewhale.service.llm.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RagPromptBuilder {

    private static final String SYSTEM =
            "你是「南鲸商城」的导购助手。只能根据下面提供的「候选商品」回答和推荐，"
            + "不得编造清单之外的商品或参数。结合用户需求说明为什么推荐、点出差异"
            + "（价格/品类/适用场景）。简洁、口语、友好；信息不足就说明。";

    private final RagProperties props;

    /** 构造 [system, user] 两条 messages；候选商品至多 context-size 条。 */
    public List<ChatMessage> build(String question, List<ProductListItemResponse> products) {
        int limit = Math.min(products.size(), props.getContextSize());
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(question).append("\n候选商品：\n");
        for (int i = 0; i < limit; i++) {
            ProductListItemResponse p = products.get(i);
            sb.append(i + 1).append(". ").append(p.getName())
              .append(" | ").append(p.getCategoryName() == null ? "" : p.getCategoryName())
              .append(" | ￥").append(p.getPrice()).append("\n");
        }
        return List.of(new ChatMessage("system", SYSTEM), new ChatMessage("user", sb.toString()));
    }
}
