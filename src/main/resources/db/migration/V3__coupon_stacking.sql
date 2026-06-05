-- ============================================================
-- Flyway 迁移 V3：多优惠券叠加（路线图任务 5）
--   1. 拆分券类型：AMOUNT_OFF → FULL_REDUCTION(满减,有门槛) / DIRECT_OFF(直减,无门槛)
--   2. 新增 order_coupon 关联表，支持一笔订单使用多张券
--
-- 【重要】已发布的 V1/V2 不可修改，结构变更一律新增版本化迁移（见决策 #43）。
-- ============================================================

SET NAMES utf8mb4;

-- ----------------------------
-- 1. 券类型拆分
-- 原 AMOUNT_OFF 仅靠 min_order_amount 是否 > 0 区分满减/直减，现拆为两个独立 type：
--   min_order_amount > 0 → FULL_REDUCTION（满减券，有门槛）
--   min_order_amount = 0 → DIRECT_OFF（直减券，无门槛）
-- 便于「同类型限一张、不同类型可叠加」的规则按 type 判定。
-- ----------------------------
UPDATE `coupon_group`
SET `type` = CASE WHEN `min_order_amount` > 0 THEN 'FULL_REDUCTION' ELSE 'DIRECT_OFF' END
WHERE `type` = 'AMOUNT_OFF';


-- ----------------------------
-- 2. Table: order_coupon（订单-优惠券关联表）
-- 一笔订单可使用多张券（每种类型至多一张），核销时逐条记录。
-- 取消/退款恢复优惠券时以本表为准（兼容历史单券订单的 orders.coupon_id）。
-- ----------------------------
DROP TABLE IF EXISTS `order_coupon`;
CREATE TABLE `order_coupon` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT             COMMENT '主键',
    `order_id`   BIGINT   NOT NULL                            COMMENT '订单ID',
    `coupon_id`  BIGINT   NOT NULL                            COMMENT '优惠券实例ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_coupon` (`order_id`, `coupon_id`),
    INDEX `idx_order_id` (`order_id`),
    CONSTRAINT `fk_order_coupon_order`  FOREIGN KEY (`order_id`)  REFERENCES `orders` (`id`),
    CONSTRAINT `fk_order_coupon_coupon` FOREIGN KEY (`coupon_id`) REFERENCES `coupon` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单-优惠券关联（支持多券叠加）';
