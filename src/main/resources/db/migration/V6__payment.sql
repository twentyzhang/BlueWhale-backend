-- ============================================================
-- Flyway 迁移 V6：模拟支付（第二轮任务 D）
--   payment  支付流水（订单 1:N 流水，trade_no 幂等键）
-- 【重要】已发布的 V1~V5 不可修改，结构变更一律新增版本化迁移（见决策 #43）。
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `payment`;

CREATE TABLE `payment` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '主键',
    `order_id`   BIGINT        NOT NULL                            COMMENT '关联订单',
    `trade_no`   VARCHAR(64)   NOT NULL                            COMMENT '交易号（幂等键）',
    `amount`     DECIMAL(10,2) NOT NULL                            COMMENT '支付金额（订单实付额快照）',
    `status`     VARCHAR(16)   NOT NULL                            COMMENT 'PENDING / SUCCESS / FAILED',
    `channel`    VARCHAR(16)   NOT NULL                            COMMENT '渠道：MOCK（未来 ALIPAY）',
    `paid_at`    DATETIME          NULL                            COMMENT '支付成功时间',
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT       NOT NULL DEFAULT 0                  COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trade_no` (`trade_no`),
    INDEX `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水';
