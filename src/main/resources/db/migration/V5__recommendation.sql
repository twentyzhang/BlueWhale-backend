-- ============================================================
-- Flyway 迁移 V5：推荐算法（路线图任务 8）
--   product_similarity  商品两两相似度（item-based CF，离线预计算的真相来源）
--
-- 派生数据：全量重建（DELETE + 批量 INSERT），无需逻辑删除列。
-- 【重要】已发布的 V1~V4 不可修改，结构变更一律新增版本化迁移（见决策 #43）。
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `product_similarity`;

CREATE TABLE `product_similarity` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT            COMMENT '主键',
    `product_id`         BIGINT        NOT NULL                           COMMENT '商品ID',
    `similar_product_id` BIGINT        NOT NULL                           COMMENT '相似商品ID',
    `score`              DECIMAL(10,8) NOT NULL                           COMMENT '加权余弦相似度 0~1',
    `created_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pair` (`product_id`, `similar_product_id`),
    INDEX `idx_product_score` (`product_id`, `score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品相似度（item-based CF 离线预计算）';
