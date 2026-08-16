-- 1、手动在 Navicat 等工具中创建 seckill 数据库或者使用上述命令在命令行执行创建
-- seckill 数据库 init （MySQL 8）
CREATE DATABASE seckill CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 2、执行下面的表 SQL 脚本
-- 2.1、执行创建用户表 t_user.sql
-- 2.2、执行创建商品表 goods.sql
-- 2.3、执行创建订单表 order_info.sql
-- 2.4、执行创建秒杀商品表 seckill_goods.sql
-- 2.5、执行创建秒杀用户表 seckill_user.sql
-- 2.6、执行创建秒杀订单表 seckill_order.sql

-- 3、执行完毕

-- 4、已有数据库的增量升级（首次全新建表可跳过）
-- 4.1、seckill_goods.goods_id 补唯一索引，避免扣库存 UPDATE 全表扫描加锁
--      若报 Duplicate entry，说明存在重复 goods_id 记录，需先清理再执行
ALTER TABLE `seckill_goods` ADD UNIQUE KEY `uk_goods_id` (`goods_id`);

-- 4.2、order_info 补查询索引（原表仅有主键，两条查询均为全表扫描）
ALTER TABLE `order_info` ADD KEY `idx_user_goods` (`user_id`,`goods_id`);
ALTER TABLE `order_info` ADD KEY `idx_user_create` (`user_id`,`create_date`);