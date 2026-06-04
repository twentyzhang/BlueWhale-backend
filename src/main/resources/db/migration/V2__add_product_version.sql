
-- ============================================================
-- Flyway 迁移 V2：为 product 表增加乐观锁版本号字段
--
-- 配合 Product 实体的 @Version 字段与 ProductMapper 的乐观锁库存更新
-- （updateStockWithVersion / updateStockTo），防止并发下单超卖。
--
-- 【重要】已发布的迁移不可再修改，后续结构变更请新增 V3__xxx.sql。
-- ============================================================

ALTER TABLE `product`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';
