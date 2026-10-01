-- SCOPED LOAD-TEST RESET TEMPLATE. REVIEW EVERY VARIABLE BEFORE EXECUTION.
-- Run only after producers are stopped and seckill.queue has drained or been intentionally purged.
-- This script avoids DROP/TRUNCATE and does not touch user/catalog data.

SET @goods_id := 1;
SET @first_user_id := 15200000000;
SET @last_user_id := 15200000099;
SET @restored_stock := 100;

START TRANSACTION;

-- Optional payment audit cleanup. Uncomment only when this table exists in the deployed schema.
-- DELETE pce
-- FROM payment_callback_event pce
-- JOIN order_info oi ON oi.id = pce.order_id
-- WHERE oi.goods_id = @goods_id
--   AND oi.user_id BETWEEN @first_user_id AND @last_user_id;

DELETE FROM seckill_order
WHERE goods_id = @goods_id
  AND user_id BETWEEN @first_user_id AND @last_user_id;

DELETE FROM order_info
WHERE goods_id = @goods_id
  AND user_id BETWEEN @first_user_id AND @last_user_id;

UPDATE seckill_goods
SET stock_count = @restored_stock,
    version = 0
WHERE goods_id = @goods_id;

-- Keep or adjust the sale window explicitly if the UI scenario depends on it:
-- UPDATE seckill_goods
-- SET start_date = DATE_SUB(NOW(), INTERVAL 5 MINUTE),
--     end_date = DATE_ADD(NOW(), INTERVAL 1 HOUR)
-- WHERE goods_id = @goods_id;

SELECT goods_id, stock_count, version, start_date, end_date
FROM seckill_goods
WHERE goods_id = @goods_id;

SELECT COUNT(*) AS remaining_scoped_orders
FROM seckill_order
WHERE goods_id = @goods_id
  AND user_id BETWEEN @first_user_id AND @last_user_id;

-- Inspect the result, then choose exactly one:
-- COMMIT;
-- ROLLBACK;
