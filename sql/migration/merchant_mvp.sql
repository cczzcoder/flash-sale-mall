-- ================================================================
-- 商家体系 MVP (shop / goods.shop_id / seckill_user.role)
-- ================================================================
-- 新库初始化与老库升级均可执行，重复执行安全（MySQL 8）。

CREATE TABLE IF NOT EXISTS `shop` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '店铺ID',
    `owner_user_id` bigint(20) NOT NULL COMMENT '店主 user_id（seckill_user.id）',
    `name` varchar(64) NOT NULL COMMENT '店铺名称',
    `logo` varchar(255) DEFAULT NULL COMMENT '店铺 Logo 图片地址',
    `description` varchar(255) DEFAULT NULL COMMENT '店铺简介',
    `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0-待审核 1-营业中 2-已停用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_user` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺';

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

CALL `add_column_if_missing`('goods', 'shop_id',
    'ALTER TABLE `goods` ADD COLUMN `shop_id` bigint(20) DEFAULT NULL COMMENT ''所属店铺ID，NULL=平台自营''');
CALL `add_column_if_missing`('seckill_user', 'role',
    'ALTER TABLE `seckill_user` ADD COLUMN `role` tinyint(4) NOT NULL DEFAULT 0 COMMENT ''角色：0-普通用户 1-商家 9-平台管理员''');

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

CALL `add_index_if_missing`('goods', 'idx_shop_id',
    'ALTER TABLE `goods` ADD KEY `idx_shop_id` (`shop_id`)');

DROP PROCEDURE IF EXISTS `add_index_if_missing`;
