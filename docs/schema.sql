-- ============================================================
-- 南鲸商城 数据库建表脚本
-- 数据库: bluewhale
-- 字符集: utf8mb4
-- 说明:
--   1. 所有表使用 deleted 字段做逻辑删除（0正常 1已删除），与 MyBatis Plus 配置保持一致
--   2. `order` 为 MySQL 保留字，表名使用 `orders`
--   3. orders 表冗余存储下单时的地址快照，防止用户修改地址后历史订单信息变动
--   4. order_item 冗余存储商品名称和单价快照，防止商品信息变更影响历史记录
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- ----------------------------
-- Table: store（商店表）
-- 先建商店，user 表中有 store_id 外键
-- ----------------------------
DROP TABLE IF EXISTS `store`;
CREATE TABLE `store` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '商店ID',
    `name`        VARCHAR(100) NOT NULL                            COMMENT '商店名称',
    `credit_code` VARCHAR(18)  NOT NULL                            COMMENT '统一社会信用代码',
    `logo`        VARCHAR(255)     NULL                            COMMENT 'Logo 图片URL',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_credit_code` (`credit_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商店表';


-- ----------------------------
-- Table: user（用户表）
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT              COMMENT '用户ID',
    `phone`      VARCHAR(11)  NOT NULL                             COMMENT '手机号（唯一）',
    `password`   VARCHAR(100) NOT NULL                             COMMENT 'BCrypt 加密密码',
    `nickname`   VARCHAR(50)  NOT NULL                             COMMENT '用户昵称',
    `role`       VARCHAR(10)  NOT NULL DEFAULT 'CUSTOMER'          COMMENT '角色: CUSTOMER / STAFF / ADMIN',
    `store_id`   BIGINT           NULL                             COMMENT 'STAFF 所属商店ID，其他角色为 NULL',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '注册时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0                   COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    INDEX `idx_role` (`role`),
    INDEX `idx_store_id` (`store_id`),
    CONSTRAINT `fk_user_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';


-- ----------------------------
-- Table: product_category（商品分类表）
-- 自关联支持嵌套，parent_id = NULL 表示顶级分类
-- ----------------------------
DROP TABLE IF EXISTS `product_category`;
CREATE TABLE `product_category` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT             COMMENT '分类ID',
    `name`       VARCHAR(50) NOT NULL                            COMMENT '分类名称',
    `parent_id`  BIGINT          NULL                            COMMENT '父分类ID，NULL 为顶级分类',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT     NOT NULL DEFAULT 0                  COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (`id`),
    INDEX `idx_parent_id` (`parent_id`),
    CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `product_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';


-- ----------------------------
-- Table: product（商品表）
-- ----------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT             COMMENT '商品ID',
    `store_id`    BIGINT         NOT NULL                            COMMENT '所属商店ID',
    `category_id` BIGINT         NOT NULL                            COMMENT '商品分类ID',
    `name`        VARCHAR(200)   NOT NULL                            COMMENT '商品名称',
    `price`       DECIMAL(10, 2) NOT NULL                            COMMENT '售价（元）',
    `stock`       INT            NOT NULL DEFAULT 0                  COMMENT '库存数量',
    `image_url`   VARCHAR(255)       NULL                            COMMENT '商品图片URL',
    `created_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT        NOT NULL DEFAULT 0                  COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (`id`),
    INDEX `idx_store_id`    (`store_id`),
    INDEX `idx_category_id` (`category_id`),
    INDEX `idx_name`        (`name`),
    INDEX `idx_price`       (`price`),
    CONSTRAINT `fk_product_store`    FOREIGN KEY (`store_id`)    REFERENCES `store`            (`id`),
    CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `product_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';


-- ----------------------------
-- Table: user_address（用户收货地址表）
-- 一个用户可维护多条地址，is_default 标记默认地址
-- ----------------------------
DROP TABLE IF EXISTS `user_address`;
CREATE TABLE `user_address` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT             COMMENT '地址ID',
    `user_id`       BIGINT      NOT NULL                            COMMENT '所属用户ID',
    `receiver_name` VARCHAR(50) NOT NULL                            COMMENT '收货人姓名',
    `phone`         VARCHAR(11) NOT NULL                            COMMENT '收货人手机号',
    `province`      VARCHAR(20) NOT NULL                            COMMENT '省',
    `city`          VARCHAR(20) NOT NULL                            COMMENT '市',
    `district`      VARCHAR(20) NOT NULL                            COMMENT '区/县',
    `detail`        VARCHAR(200) NOT NULL                           COMMENT '详细地址',
    `is_default`    TINYINT     NOT NULL DEFAULT 0                  COMMENT '是否默认: 0否 1是',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT     NOT NULL DEFAULT 0                  COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    CONSTRAINT `fk_address_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址表';


-- ----------------------------
-- Table: cart（购物车表）
-- 一个用户对应一个购物车，首次加购时自动创建
-- ----------------------------
DROP TABLE IF EXISTS `cart`;
CREATE TABLE `cart` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT             COMMENT '购物车ID',
    `user_id`    BIGINT   NOT NULL                            COMMENT '所属用户ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';


-- ----------------------------
-- Table: cart_item（购物车条目表）
-- 同一购物车中同一商品唯一，数量叠加
-- ----------------------------
DROP TABLE IF EXISTS `cart_item`;
CREATE TABLE `cart_item` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT             COMMENT '条目ID',
    `cart_id`    BIGINT   NOT NULL                            COMMENT '购物车ID',
    `product_id` BIGINT   NOT NULL                            COMMENT '商品ID',
    `quantity`   INT      NOT NULL DEFAULT 1                  COMMENT '数量',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '加购时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cart_product` (`cart_id`, `product_id`),
    INDEX `idx_cart_id` (`cart_id`),
    CONSTRAINT `fk_cart_item_cart`    FOREIGN KEY (`cart_id`)    REFERENCES `cart`    (`id`),
    CONSTRAINT `fk_cart_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车条目表';


-- ----------------------------
-- Table: coupon_group（优惠券组表）
-- store_id = NULL 表示全局优惠券（由 Admin 发布）
-- type: DISCOUNT（折扣券，value 为折扣率，如 0.8 表示八折）
--       AMOUNT_OFF（满减券，value 为减免金额，单位元）
-- ----------------------------
DROP TABLE IF EXISTS `coupon_group`;
CREATE TABLE `coupon_group` (
    `id`               BIGINT         NOT NULL AUTO_INCREMENT             COMMENT '优惠券组ID',
    `store_id`         BIGINT             NULL                            COMMENT '所属商店ID，NULL 为全局',
    `name`             VARCHAR(100)   NOT NULL                            COMMENT '优惠券名称',
    `type`             VARCHAR(15)    NOT NULL                            COMMENT '类型: DISCOUNT / AMOUNT_OFF',
    `value`            DECIMAL(10, 2) NOT NULL                            COMMENT '面值：折扣率或减免金额',
    `min_order_amount` DECIMAL(10, 2) NOT NULL DEFAULT 0.00               COMMENT '最低使用金额（0表示无门槛）',
    `total_count`      INT            NOT NULL                            COMMENT '发放总量',
    `remain_count`     INT            NOT NULL                            COMMENT '剩余可领数量',
    `start_at`         DATETIME       NOT NULL                            COMMENT '生效时间',
    `expire_at`        DATETIME       NOT NULL                            COMMENT '过期时间',
    `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                               ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT        NOT NULL DEFAULT 0                  COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (`id`),
    INDEX `idx_store_id`  (`store_id`),
    INDEX `idx_expire_at` (`expire_at`),
    CONSTRAINT `fk_coupon_group_store` FOREIGN KEY (`store_id`) REFERENCES `store` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券组表';


-- ----------------------------
-- Table: coupon（优惠券实例表）
-- 每次用户领取生成一条记录
-- status: UNUSED / USED / EXPIRED
-- ----------------------------
DROP TABLE IF EXISTS `coupon`;
CREATE TABLE `coupon` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT             COMMENT '优惠券ID',
    `group_id`   BIGINT      NOT NULL                            COMMENT '所属优惠券组ID',
    `user_id`    BIGINT      NOT NULL                            COMMENT '持有用户ID',
    `status`     VARCHAR(10) NOT NULL DEFAULT 'UNUSED'           COMMENT '状态: UNUSED / USED / EXPIRED',
    `claimed_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '领取时间',
    `used_at`    DATETIME        NULL                            COMMENT '使用时间',
    `order_id`   BIGINT          NULL                            COMMENT '使用于哪个订单',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id`  (`user_id`),
    INDEX `idx_group_id` (`group_id`),
    INDEX `idx_status`   (`status`),
    CONSTRAINT `fk_coupon_group` FOREIGN KEY (`group_id`) REFERENCES `coupon_group` (`id`),
    CONSTRAINT `fk_coupon_user`  FOREIGN KEY (`user_id`)  REFERENCES `user`         (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券实例表';


-- ----------------------------
-- Table: orders（订单表）
-- 地址字段冗余存储下单时的快照，避免用户修改地址影响历史订单
-- status: PENDING_PAYMENT / PAID / SHIPPED / COMPLETED / CANCELLED
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
    `id`                BIGINT         NOT NULL AUTO_INCREMENT             COMMENT '订单ID',
    `user_id`           BIGINT         NOT NULL                            COMMENT '下单用户ID',
    `store_id`          BIGINT         NOT NULL                            COMMENT '所属商店ID',
    `status`            VARCHAR(20)    NOT NULL DEFAULT 'PENDING_PAYMENT'  COMMENT '订单状态',
    `total_amount`      DECIMAL(10, 2) NOT NULL                            COMMENT '商品总金额（折扣前）',
    `discount_amount`   DECIMAL(10, 2) NOT NULL DEFAULT 0.00               COMMENT '优惠折扣金额',
    `payable_amount`    DECIMAL(10, 2) NOT NULL                            COMMENT '实付金额',
    `coupon_id`         BIGINT             NULL                            COMMENT '使用的优惠券ID',
    -- 收货地址快照
    `addr_receiver_name` VARCHAR(50)   NOT NULL                            COMMENT '收货人姓名（快照）',
    `addr_phone`         VARCHAR(11)   NOT NULL                            COMMENT '收货人手机号（快照）',
    `addr_province`      VARCHAR(20)   NOT NULL                            COMMENT '省（快照）',
    `addr_city`          VARCHAR(20)   NOT NULL                            COMMENT '市（快照）',
    `addr_district`      VARCHAR(20)   NOT NULL                            COMMENT '区/县（快照）',
    `addr_detail`        VARCHAR(200)  NOT NULL                            COMMENT '详细地址（快照）',
    -- 物流信息
    `tracking_number`   VARCHAR(50)        NULL                            COMMENT '快递单号',
    `carrier`           VARCHAR(50)        NULL                            COMMENT '快递公司',
    -- 时间节点
    `created_at`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `paid_at`           DATETIME           NULL                            COMMENT '支付时间',
    `shipped_at`        DATETIME           NULL                            COMMENT '发货时间',
    `completed_at`      DATETIME           NULL                            COMMENT '完成时间',
    `cancelled_at`      DATETIME           NULL                            COMMENT '取消时间',
    `updated_at`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                               ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT        NOT NULL DEFAULT 0                  COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id`  (`user_id`),
    INDEX `idx_store_id` (`store_id`),
    INDEX `idx_status`   (`status`),
    INDEX `idx_created_at` (`created_at`),
    CONSTRAINT `fk_orders_user`   FOREIGN KEY (`user_id`)  REFERENCES `user`  (`id`),
    CONSTRAINT `fk_orders_store`  FOREIGN KEY (`store_id`) REFERENCES `store` (`id`),
    CONSTRAINT `fk_orders_coupon` FOREIGN KEY (`coupon_id`) REFERENCES `coupon` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';


-- ----------------------------
-- Table: order_item（订单条目表）
-- 商品名称和单价存为快照，防止商品信息变更后历史订单不一致
-- ----------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT             COMMENT '条目ID',
    `order_id`      BIGINT         NOT NULL                            COMMENT '所属订单ID',
    `product_id`    BIGINT         NOT NULL                            COMMENT '商品ID（用于跳转详情）',
    `product_name`  VARCHAR(200)   NOT NULL                            COMMENT '商品名称（快照）',
    `product_image` VARCHAR(255)       NULL                            COMMENT '商品图片URL（快照）',
    `unit_price`    DECIMAL(10, 2) NOT NULL                            COMMENT '下单时单价（快照）',
    `quantity`      INT            NOT NULL                            COMMENT '购买数量',
    `subtotal`      DECIMAL(10, 2) NOT NULL                            COMMENT '小计 = unit_price × quantity',
    `created_at`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_order_id`   (`order_id`),
    INDEX `idx_product_id` (`product_id`),
    CONSTRAINT `fk_order_item_order`   FOREIGN KEY (`order_id`)   REFERENCES `orders`  (`id`),
    CONSTRAINT `fk_order_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单条目表';


-- ----------------------------
-- Table: review（评论表）
-- parent_id = NULL 为一级评价（含评分），parent_id 有值为回复（无评分）
-- order_id 用于校验顾客是否有购买资格（发一级评论时验证）
-- ----------------------------
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '评论ID',
    `product_id` BIGINT       NOT NULL                            COMMENT '被评价商品ID',
    `user_id`    BIGINT       NOT NULL                            COMMENT '评论用户ID',
    `order_id`   BIGINT           NULL                            COMMENT '关联订单ID（一级评论必填，用于验证购买）',
    `parent_id`  BIGINT           NULL                            COMMENT '父评论ID，NULL 表示一级评价',
    `rating`     TINYINT          NULL                            COMMENT '评分 1-5（仅一级评价有值）',
    `content`    VARCHAR(500) NOT NULL                            COMMENT '评论内容',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '评论时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (`id`),
    INDEX `idx_product_id` (`product_id`),
    INDEX `idx_user_id`    (`user_id`),
    INDEX `idx_parent_id`  (`parent_id`),
    CONSTRAINT `fk_review_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
    CONSTRAINT `fk_review_user`    FOREIGN KEY (`user_id`)    REFERENCES `user`    (`id`),
    CONSTRAINT `fk_review_order`   FOREIGN KEY (`order_id`)   REFERENCES `orders`  (`id`),
    CONSTRAINT `fk_review_parent`  FOREIGN KEY (`parent_id`)  REFERENCES `review`  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评论表';


SET FOREIGN_KEY_CHECKS = 1;


-- ============================================================
-- 初始化数据：创建默认管理员账号
-- 密码明文: Admin@123456（BCrypt 加密后存储，此处为示例占位）
-- 实际部署时应通过初始化脚本或首次启动程序生成真实 BCrypt 哈希
-- ============================================================
INSERT INTO `store` (`name`, `credit_code`, `logo`)
VALUES ('平台自营', '00000000000000000A', NULL);

INSERT INTO `user` (`phone`, `password`, `nickname`, `role`, `store_id`)
VALUES (
    '10000000000',
    '$2a$10$placeholderBCryptHashReplaceBeforeDeployment000000',
    '超级管理员',
    'ADMIN',
    NULL
);
