# Repeatable load-test reset

Use only in an isolated, authorized test environment. These assets intentionally avoid `FLUSHALL`, `FLUSHDB`, broad table truncation, and user/catalog deletion.

## Required sequence

1. Stop JMeter and block new seckill producers.
2. Wait for RabbitMQ `seckill.queue` to reach zero messages and zero unacknowledged deliveries twice, or stop consumers and intentionally purge only that test queue. Inspect `seckill.dead.queue` separately; do not silently delete evidence of failures.
3. Back up or snapshot state when needed.
4. Edit `reset-scoped.sql` variables. Run it in a MySQL client, inspect its result, then issue `COMMIT` (or `ROLLBACK`). It deletes only the selected goods/user range and restores that goods row's stock.
5. Run `reset-redis.sh` with the same goods, user range, and restored stock:

```sh
GOODS_ID=1 RESTORED_STOCK=100 \
FIRST_USER_ID=15200000000 LAST_USER_ID=15200000099 \
REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_DB=0 \
  bash load-test/reset/reset-redis.sh
```

6. Restart the application if desired so startup stock preload reads the restored DB value. Verify `GoodsKey:gs<goodsId>` equals `seckill_goods.stock_count` before traffic resumes.
7. Confirm no scoped rows remain in `seckill_order`, queue depth is zero, sale dates cover the test window, and the expected test users can log in.

Authentication keys (`SeckillUserKey:tk...`) are deliberately retained, so existing sessions remain valid. Delete only known test tokens if the scenario requires fresh login; never scan-delete all tokens on a shared Redis instance.

The SQL leaves payment callback cleanup commented because payment schema assets are not part of the checked-in SQL baseline. Enable it only after verifying the target table and relationships. Old in-flight messages must never survive a reset: they can create orders and decrement the newly restored stock after the reset.
