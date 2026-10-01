-- 订单信息表
DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info` (
                              `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '订单ID',
                              `user_id` bigint(20) DEFAULT NULL COMMENT '用户ID',
                              `goods_id` bigint(20) DEFAULT NULL COMMENT '商品ID',
                              `delivery_addr_id` bigint(20) DEFAULT NULL COMMENT '地址',
                              `goods_name` varchar(16) DEFAULT NULL COMMENT '商品名称',
                              `goods_count` int(11) DEFAULT '0' COMMENT '商品数量',
                              `goods_price` decimal(10,2) DEFAULT '0.00' COMMENT '商品价格',
                              `order_channel` tinyint(4) DEFAULT '0' COMMENT '订单渠道',
                              `order_status` tinyint(4) DEFAULT '0' COMMENT '订单状态',
                              `create_date` datetime DEFAULT NULL COMMENT '创建时间',
                              `pay_date` datetime DEFAULT NULL COMMENT '支付时间',
                              `payment_no` varchar(64) DEFAULT NULL COMMENT '商户支付单号',
                              `payment_transaction_id` varchar(64) DEFAULT NULL COMMENT '支付平台交易号',
                              `close_date` datetime DEFAULT NULL COMMENT '关闭时间',
                              `shipping_company` varchar(32) DEFAULT NULL COMMENT '物流公司',
                              `tracking_number` varchar(64) DEFAULT NULL COMMENT '物流单号',
                              `shipped_date` datetime DEFAULT NULL COMMENT '发货时间',
                              `refund_reason` varchar(255) DEFAULT NULL COMMENT '退款原因',
                              `refund_request_date` datetime DEFAULT NULL COMMENT '退款申请时间',
                              `refund_date` datetime DEFAULT NULL COMMENT '退款时间',
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_payment_no` (`payment_no`),
                              UNIQUE KEY `uk_payment_transaction_id` (`payment_transaction_id`),
                              -- selectOrderInfo 按 (user_id, goods_id) 查询
                              KEY `idx_user_goods` (`user_id`,`goods_id`),
                              -- selectRecentByUserId 按 user_id 过滤 + create_date 倒序取 N 条
                              KEY `idx_user_create` (`user_id`,`create_date`),
                              KEY `idx_status_create_id` (`order_status`,`create_date`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单信息表';


INSERT INTO `order_info` (`id`,`user_id`,`goods_id`,`delivery_addr_id`,`goods_name`,`goods_count`,`goods_price`,`order_channel`,`order_status`,`create_date`,`pay_date`) VALUES ('335', '15008491407', '4', null, null, '1', '0.04', '1', '0', '2019-05-28 10:54:56', null);
INSERT INTO `order_info` (`id`,`user_id`,`goods_id`,`delivery_addr_id`,`goods_name`,`goods_count`,`goods_price`,`order_channel`,`order_status`,`create_date`,`pay_date`) VALUES ('336', '15008491407', '1', null, null, '1', '0.01', '1', '0', '2019-05-28 11:37:25', null);
