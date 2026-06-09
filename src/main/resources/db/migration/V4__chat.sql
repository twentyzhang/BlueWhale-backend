-- ============================================================
-- Flyway 迁移 V4：实时客服（路线图任务 7）
--   chat_session  会话（一个买家 ↔ 一个店铺，可复用，认领制）
--   chat_message  消息（append-only）
--
-- 【重要】已发布的 V1~V3 不可修改，结构变更一律新增版本化迁移（见决策 #43）。
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_session`;

-- ----------------------------
-- Table: chat_session
-- UNIQUE(store_id, customer_id)：同一买家×店铺永远一个会话
-- assignee_staff_id：接待客服，NULL=未接入（认领制核心，决策 #50）
-- last_message / last_message_at：冗余，会话列表排序与预览，避免 N+1
-- ----------------------------
CREATE TABLE `chat_session` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '会话ID',
    `store_id`          BIGINT       NOT NULL                            COMMENT '归属店铺',
    `customer_id`       BIGINT       NOT NULL                            COMMENT '买家',
    `assignee_staff_id` BIGINT           NULL                            COMMENT '接待客服，NULL=未接入',
    `last_message`      VARCHAR(120)     NULL                            COMMENT '最后一条消息预览',
    `last_message_at`   DATETIME         NULL                            COMMENT '最后消息时间（列表排序）',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '更新时间',
    `deleted`           TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除 0正常 1删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_store_customer` (`store_id`, `customer_id`),
    INDEX `idx_store_id` (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话';

-- ----------------------------
-- Table: chat_message（append-only，无 updated_at）
-- INDEX(session_id, id)：历史消息按会话游标分页
-- ----------------------------
CREATE TABLE `chat_message` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT             COMMENT '消息ID',
    `session_id`  BIGINT        NOT NULL                            COMMENT '所属会话',
    `sender_role` VARCHAR(16)   NOT NULL                            COMMENT 'CUSTOMER / STAFF',
    `sender_id`   BIGINT        NOT NULL                            COMMENT '发送者 userId',
    `content`     VARCHAR(1000) NOT NULL                            COMMENT '消息内容',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `deleted`     TINYINT       NOT NULL DEFAULT 0                  COMMENT '逻辑删除 0正常 1删除',
    PRIMARY KEY (`id`),
    INDEX `idx_session_cursor` (`session_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服消息';
