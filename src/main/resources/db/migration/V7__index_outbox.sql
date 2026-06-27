-- ============================================================
-- Flyway 迁移 V7：索引 outbox（AI 语义搜索）
--   index_outbox  商品变更 → 向量索引 的事务性 outbox 事件表
-- 【重要】已发布的 V1~V6 不可修改，结构变更一律新增版本化迁移（见决策 #43）。
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `index_outbox`;

CREATE TABLE `index_outbox` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '主键',
    `product_id`  BIGINT       NOT NULL                            COMMENT '关联商品',
    `op`          VARCHAR(8)   NOT NULL                            COMMENT 'UPSERT / DELETE',
    `status`      VARCHAR(8)   NOT NULL DEFAULT 'PENDING'          COMMENT 'PENDING / DONE / FAILED',
    `retry_count` INT          NOT NULL DEFAULT 0                  COMMENT '已重试次数',
    `last_error`  VARCHAR(512)     NULL                            COMMENT '最近失败原因',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                       ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_status_id` (`status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='索引同步 outbox 事件';
