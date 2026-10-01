-- 1、手动在 Navicat 等工具中创建 seckill 数据库或者使用上述命令在命令行执行创建
-- seckill 数据库 init （MySQL 8）
CREATE DATABASE seckill CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 2、执行下面的基础表 SQL 脚本（新库初始化）
-- 2.1、执行创建用户表 t_user.sql
-- 2.2、执行创建商品表 goods.sql
-- 2.3、执行创建订单表 order_info.sql
-- 2.4、执行创建秒杀商品表 seckill_goods.sql
-- 2.5、执行创建秒杀用户表 seckill_user.sql
-- 2.6、执行创建秒杀订单表 seckill_order.sql
-- 2.7、执行 sql/migration/upgrade_to_current.sql（补齐地址、支付、物流、退款和索引）

-- 3、执行完毕

-- 4、已有数据库升级：只执行 sql/migration/upgrade_to_current.sql
--    不要再执行已移除的分版本迁移文件。
