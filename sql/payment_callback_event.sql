-- 支付回调审计表（全新数据库初始化使用）
DROP TABLE IF EXISTS `payment_callback_event`;
CREATE TABLE `payment_callback_event` (
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
