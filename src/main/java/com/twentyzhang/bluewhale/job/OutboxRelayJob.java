package com.twentyzhang.bluewhale.job;

import com.twentyzhang.bluewhale.config.SearchProperties;
import com.twentyzhang.bluewhale.entity.IndexOutbox;
import com.twentyzhang.bluewhale.entity.Product;
import com.twentyzhang.bluewhale.entity.ProductCategory;
import com.twentyzhang.bluewhale.mapper.IndexOutboxMapper;
import com.twentyzhang.bluewhale.mapper.ProductCategoryMapper;
import com.twentyzhang.bluewhale.mapper.ProductMapper;
import com.twentyzhang.bluewhale.service.EmbeddingClient;
import com.twentyzhang.bluewhale.service.ProductVectorIndex;
import com.twentyzhang.bluewhale.service.vector.VectorPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 索引 outbox 中继：轮询 PENDING 事件 → 调通义 embedding → 同步 Qdrant → 标记 DONE/重试/FAILED。
 * 系统级调用，不读 SecurityContext。Qdrant 不可用时整批跳过、事件留 PENDING 下轮重试。
 * {@code @EnableScheduling} 已在 OrderAutocancelJob 全局开启。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final IndexOutboxMapper outboxMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final EmbeddingClient embeddingClient;
    private final ProductVectorIndex vectorIndex;
    private final SearchProperties props;

    @Scheduled(fixedDelayString = "${search.outbox.poll-delay-ms:5000}")
    public void pollAndProcess() {
        List<IndexOutbox> events = outboxMapper.selectPending(props.getOutbox().getPollBatchSize());
        if (events.isEmpty()) return;

        try {
            vectorIndex.ensureCollection();
        } catch (Exception e) {
            log.warn("Qdrant 不可用，跳过本轮索引同步：{}", e.getMessage());
            return; // 事件留 PENDING，下轮重试
        }

        for (IndexOutbox e : events) {
            try {
                process(e);
                outboxMapper.markDone(e.getId());
            } catch (Exception ex) {
                String err = truncate(ex.getMessage());
                if (e.getRetryCount() + 1 >= props.getOutbox().getMaxRetry()) {
                    outboxMapper.markFailed(e.getId(), err);
                    log.error("索引事件 {} 重试超限置 FAILED：{}", e.getId(), err);
                } else {
                    outboxMapper.markRetry(e.getId(), err);
                }
            }
        }
    }

    private void process(IndexOutbox e) {
        if ("DELETE".equals(e.getOp())) {
            vectorIndex.delete(e.getProductId());
            return;
        }
        Product p = productMapper.selectById(e.getProductId());
        if (p == null) {                       // UPSERT 但商品已不存在/已删 → 退化为删除
            vectorIndex.delete(e.getProductId());
            return;
        }
        float[] vec = embeddingClient.embed(buildText(p));
        vectorIndex.upsert(p.getId(), vec,
                new VectorPayload(p.getStoreId(), p.getCategoryId(), p.getPrice(), p.getName()));
    }

    private String buildText(Product p) {
        String cat = "";
        if (p.getCategoryId() != null) {
            ProductCategory c = productCategoryMapper.selectById(p.getCategoryId());
            if (c != null) cat = c.getName();
        }
        return cat.isBlank() ? p.getName() : p.getName() + " " + cat;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
