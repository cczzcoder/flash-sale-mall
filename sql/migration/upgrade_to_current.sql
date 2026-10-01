-- ================================================================
-- seckill current schema upgrade (V2 -> V7)
-- ================================================================
-- Use this single file for an existing database. It is safe to run
-- repeatedly on MySQL 8: columns/tables are guarded, and indexes are
-- created only when they are missing.

CREATE TABLE IF NOT EXISTS `payment_callback_event` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `order_id` bigint(20) NOT NULL,
    `payment_no` varchar(64) NOT NULL,
    `provider` varchar(16) NOT NULL,
    `transaction_id` varchar(64) NOT NULL,
    `process_result` varchar(16) NOT NULL,
    `create_date` datetime NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_transaction` (`provider`,`transaction_id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调审计表';

CREATE TABLE IF NOT EXISTS `delivery_address` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_id` bigint(20) NOT NULL,
    `receiver_name` varchar(32) NOT NULL,
    `receiver_mobile` varchar(16) NOT NULL,
    `province` varchar(32) NOT NULL,
    `city` varchar(32) NOT NULL,
    `district` varchar(32) NOT NULL,
    `detail` varchar(128) NOT NULL,
    `default_address` tinyint(1) NOT NULL DEFAULT 0,
    `create_date` datetime NOT NULL,
    `update_date` datetime NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_address_user_default` (`user_id`,`default_address`,`update_date`),
    CONSTRAINT `fk_delivery_address_user` FOREIGN KEY (`user_id`) REFERENCES `seckill_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `admin_user` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `username` varchar(64) NOT NULL,
    `password` varchar(32) NOT NULL COMMENT 'MD5 form password plus salt, never store plaintext',
    `salt` varchar(10) NOT NULL,
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP PROCEDURE IF EXISTS `add_column_if_missing`;
DELIMITER $$
CREATE PROCEDURE `add_column_if_missing`(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column
    ) THEN
        SET @schema_upgrade_ddl = p_ddl;
        PREPARE schema_upgrade_stmt FROM @schema_upgrade_ddl;
        EXECUTE schema_upgrade_stmt;
        DEALLOCATE PREPARE schema_upgrade_stmt;
    END IF;
END$$
DELIMITER ;

CALL `add_column_if_missing`('order_info', 'payment_no',
    'ALTER TABLE `order_info` ADD COLUMN `payment_no` varchar(64) DEFAULT NULL COMMENT ''商户支付单号'' AFTER `pay_date`');
CALL `add_column_if_missing`('order_info', 'payment_transaction_id',
    'ALTER TABLE `order_info` ADD COLUMN `payment_transaction_id` varchar(64) DEFAULT NULL COMMENT ''支付平台交易号'' AFTER `payment_no`');
CALL `add_column_if_missing`('order_info', 'close_date',
    'ALTER TABLE `order_info` ADD COLUMN `close_date` datetime DEFAULT NULL COMMENT ''关闭时间'' AFTER `payment_transaction_id`');
CALL `add_column_if_missing`('order_info', 'delivery_receiver_name',
    'ALTER TABLE `order_info` ADD COLUMN `delivery_receiver_name` varchar(32) DEFAULT NULL AFTER `delivery_addr_id`');
CALL `add_column_if_missing`('order_info', 'delivery_receiver_mobile',
    'ALTER TABLE `order_info` ADD COLUMN `delivery_receiver_mobile` varchar(16) DEFAULT NULL AFTER `delivery_receiver_name`');
CALL `add_column_if_missing`('order_info', 'delivery_address',
    'ALTER TABLE `order_info` ADD COLUMN `delivery_address` varchar(256) DEFAULT NULL AFTER `delivery_receiver_mobile`');
CALL `add_column_if_missing`('order_info', 'shipping_company',
    'ALTER TABLE `order_info` ADD COLUMN `shipping_company` varchar(32) DEFAULT NULL COMMENT ''物流公司'' AFTER `close_date`');
CALL `add_column_if_missing`('order_info', 'tracking_number',
    'ALTER TABLE `order_info` ADD COLUMN `tracking_number` varchar(64) DEFAULT NULL COMMENT ''物流单号'' AFTER `shipping_company`');
CALL `add_column_if_missing`('order_info', 'shipped_date',
    'ALTER TABLE `order_info` ADD COLUMN `shipped_date` datetime DEFAULT NULL COMMENT ''发货时间'' AFTER `tracking_number`');
CALL `add_column_if_missing`('order_info', 'refund_reason',
    'ALTER TABLE `order_info` ADD COLUMN `refund_reason` varchar(255) DEFAULT NULL COMMENT ''退款原因''');
CALL `add_column_if_missing`('order_info', 'refund_request_date',
    'ALTER TABLE `order_info` ADD COLUMN `refund_request_date` datetime DEFAULT NULL COMMENT ''退款申请时间''');
CALL `add_column_if_missing`('order_info', 'refund_date',
    'ALTER TABLE `order_info` ADD COLUMN `refund_date` datetime DEFAULT NULL COMMENT ''退款时间''');

DROP PROCEDURE IF EXISTS `add_column_if_missing`;

DROP PROCEDURE IF EXISTS `add_index_if_missing`;
DELIMITER $$
CREATE PROCEDURE `add_index_if_missing`(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index
    ) THEN
        SET @schema_upgrade_ddl = p_ddl;
        PREPARE schema_upgrade_stmt FROM @schema_upgrade_ddl;
        EXECUTE schema_upgrade_stmt;
        DEALLOCATE PREPARE schema_upgrade_stmt;
    END IF;
END$$
DELIMITER ;

CALL `add_index_if_missing`('order_info', 'uk_payment_no',
    'ALTER TABLE `order_info` ADD UNIQUE KEY `uk_payment_no` (`payment_no`)');
CALL `add_index_if_missing`('order_info', 'uk_payment_transaction_id',
    'ALTER TABLE `order_info` ADD UNIQUE KEY `uk_payment_transaction_id` (`payment_transaction_id`)');
CALL `add_index_if_missing`('order_info', 'idx_user_goods',
    'ALTER TABLE `order_info` ADD KEY `idx_user_goods` (`user_id`,`goods_id`)');
CALL `add_index_if_missing`('order_info', 'idx_user_create',
    'ALTER TABLE `order_info` ADD KEY `idx_user_create` (`user_id`,`create_date`)');
CALL `add_index_if_missing`('order_info', 'idx_status_create_id',
    'ALTER TABLE `order_info` ADD KEY `idx_status_create_id` (`order_status`,`create_date`,`id`)');
CALL `add_index_if_missing`('seckill_goods', 'uk_goods_id',
    'ALTER TABLE `seckill_goods` ADD UNIQUE KEY `uk_goods_id` (`goods_id`)');

DROP PROCEDURE IF EXISTS `add_index_if_missing`;
