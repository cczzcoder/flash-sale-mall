-- Demo data refresh script.
-- Run after the base tables and upgrade_to_current.sql, before starting the application.
-- It is intentionally repeatable and does not delete user/order data.
USE `seckill`;

-- Keep the first item live for the walkthrough and provide additional states.
UPDATE `goods`
SET `goods_name` = 'iPhone X 64G',
    `goods_title` = 'Apple iPhone X 64G 银色',
    `goods_img` = '/img/iphonex.png',
    `goods_price` = 5000.00
WHERE `id` = 1;

UPDATE `seckill_goods`
SET `seckill_price` = 0.01,
    `stock_count` = 20,
    `start_date` = DATE_SUB(NOW(), INTERVAL 2 MINUTE),
    `end_date` = DATE_ADD(NOW(), INTERVAL 30 MINUTE)
WHERE `goods_id` = 1;

UPDATE `seckill_goods`
SET `start_date` = DATE_ADD(NOW(), INTERVAL 3 MINUTE),
    `end_date` = DATE_ADD(NOW(), INTERVAL 33 MINUTE),
    `stock_count` = 20
WHERE `goods_id` = 2;

UPDATE `seckill_goods`
SET `start_date` = DATE_SUB(NOW(), INTERVAL 2 HOUR),
    `end_date` = DATE_SUB(NOW(), INTERVAL 1 HOUR)
WHERE `goods_id` = 3;

-- A known demo user from seckill_user.sql receives a default address.
INSERT INTO `delivery_address`
    (`user_id`, `receiver_name`, `receiver_mobile`, `province`, `city`, `district`, `detail`, `default_address`, `create_date`, `update_date`)
SELECT 15008888888, '演示用户', '13800138000', '上海市', '上海市', '浦东新区', '世纪大道 100 号', 1, NOW(), NOW()
WHERE EXISTS (SELECT 1 FROM `seckill_user` WHERE `id` = 15008888888)
  AND NOT EXISTS (
      SELECT 1 FROM `delivery_address`
      WHERE `user_id` = 15008888888 AND `receiver_mobile` = '13800138000'
  );

-- Redis stock is loaded from seckill_goods on application startup.
