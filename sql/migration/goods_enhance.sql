-- ================================================================
-- 商城丰富化 (goods.category / seckill_goods.stock_total)
-- ================================================================
-- 新库初始化与老库升级均可执行，重复执行安全（MySQL 8）。

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

-- 商品分类（列表页筛选用），NULL/空 表示未分类
CALL `add_column_if_missing`('goods', 'category',
    'ALTER TABLE `goods` ADD COLUMN `category` varchar(32) DEFAULT NULL COMMENT ''商品分类，NULL=未分类''');

-- 秒杀总库存（= 已售 + 剩余），用于"已抢购百分比"进度条；
-- 剩余库存仍走 stock_count（秒杀扣减只动 stock_count），补货时两者同增。
CALL `add_column_if_missing`('seckill_goods', 'stock_total',
    'ALTER TABLE `seckill_goods` ADD COLUMN `stock_total` int(11) DEFAULT NULL COMMENT ''秒杀总库存（已售+剩余，进度条分母）''');

-- 存量活动初始化：总库存 = 当前剩余（进度从 0 开始累计）
UPDATE `seckill_goods` SET `stock_total` = `stock_count` WHERE `stock_total` IS NULL;

-- 演示数据分类（仅填充经典示例手机商品，其余保持未分类）
UPDATE `goods` SET `category` = '手机数码' WHERE `category` IS NULL AND `id` BETWEEN 1 AND 6;

DROP PROCEDURE IF EXISTS `add_column_if_missing`;
