#!/usr/bin/env bash
set -euo pipefail

: "${REDIS_HOST:=127.0.0.1}"
: "${REDIS_PORT:=6379}"
: "${REDIS_DB:=0}"
: "${GOODS_ID:?Set GOODS_ID}"
: "${RESTORED_STOCK:?Set RESTORED_STOCK to match seckill_goods.stock_count}"
: "${FIRST_USER_ID:?Set FIRST_USER_ID}"
: "${LAST_USER_ID:?Set LAST_USER_ID}"

redis=(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB")
[[ -n "${REDIS_PASSWORD:-}" ]] && redis+=(--no-auth-warning -a "$REDIS_PASSWORD")

# Never use FLUSHALL/FLUSHDB: DB 0 may be shared. Delete only application keys
# for this goods/user range. UNLINK is non-blocking on supported Redis versions.
"${redis[@]}" UNLINK "SeckillKey:go${GOODS_ID}" "GoodsKey:gd${GOODS_ID}" "GoodsKey:gl" >/dev/null

for ((uid=FIRST_USER_ID; uid<=LAST_USER_ID; uid++)); do
  "${redis[@]}" UNLINK \
    "OrderKey:ms_uid_gid${uid}_${GOODS_ID}" \
    "SeckillKey:mp${uid}_${GOODS_ID}" \
    "SeckillKey:vc${uid}_${GOODS_ID}" \
    "AccessKey:access/seckill/verifyCode_${uid}" \
    "AccessKey:access/seckill/getPath_${uid}" \
    "AccessKey:access/seckill/result_${uid}" >/dev/null
done

# Rewrite stock last, after stale sold-out/order/path state is gone.
"${redis[@]}" SET "GoodsKey:gs${GOODS_ID}" "$RESTORED_STOCK" >/dev/null
actual=$("${redis[@]}" GET "GoodsKey:gs${GOODS_ID}")
[[ "$actual" == "$RESTORED_STOCK" ]] || { printf 'stock verification failed: %s\n' "$actual" >&2; exit 1; }
printf 'Reset scoped Redis state for goods %s, users %s..%s; stock=%s\n' \
  "$GOODS_ID" "$FIRST_USER_ID" "$LAST_USER_ID" "$actual"
