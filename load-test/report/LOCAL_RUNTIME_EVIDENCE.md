# Local runtime evidence

This is a controlled local verification record for the current working tree. It is not a substitute for a JMeter load test.

## Runtime

| Item | Observed value |
|---|---|
| Application | `http://localhost:8080`, Spring Boot health `UP` |
| Redis | `127.0.0.1:6379`, `GoodsKey:gs1` = `10` |
| RabbitMQ | `127.0.0.1:5672`, ready/unacknowledged messages `0/0` for `seckill.queue` and `seckill.dead.queue` |
| Prometheus | `http://localhost:9090`, ready; all 5 configured targets `up=1` |
| Grafana | `http://localhost:3000`, API health `database: ok`, version `12.1.0` |
| Blackbox | Probe of `/actuator/health` returned HTTP `200`, `probe_success=1` |
| JMeter | Apache JMeter `5.6.3` installed at `D:\tools\apache-jmeter-5.6.3`; 1-thread smoke and 500/1000-thread ingress runs passed |
| Verification window | `2026-08-27 17:20:37 - 17:22:40 +08:00` |

## Current rebuild smoke verification

This is a separate route-level check after applying V3/V4 and rebuilding the current working tree. It did not reset data, create orders, consume stock, or start JMeter.

| Check | Observed value |
|---|---|
| Build | `mvn package -DskipTests` passed; `target/seckill.jar` contains `BOOT-INF/classes/templates/login.html` |
| Runtime | PID `34316`, `http://localhost:8080`, Spring Boot health `UP` |
| Pages | `/login/index`, `/register.htm`, `/address.htm`, `/admin.htm` returned HTTP `200` |
| Goods detail | `/goods/detailStatic/1` returned HTTP `200`, JSON `code=0` |
| Access control | Unauthenticated `/address/list` returned `code=500210`; unauthenticated `/admin/goods/list` returned `code=500220` |
| Log scan | No `ERROR`, `Exception`, or `TemplateInputException` in current application logs |
| Verification time | `2026-08-28 09:11 +08:00` |

## Database snapshot

Database: `seckill` on local MySQL. The new database was initialized from the checked-in SQL assets and `payment_callback_event` was applied.

| Query/result | Value |
|---|---:|
| `goods` rows | 6 |
| `seckill_goods` rows | 6 |
| `seckill_user` rows | 1002 |
| `order_info` rows | 4 |
| `payment_callback_event` rows | 2 |
| `seckill_order` rows | 0 |
| `seckill_goods.stock_count` for goods 1 | 10 |
| Redis `GoodsKey:gs1` | 10 |

The two seeded historical orders were closed by the timeout task. Orders `337` and `338` were controlled payment-verification orders for user `15200000000`; both ended with `order_status=1`. No seckill workload was run, so a stock-consumption invariant is not claimed.

## Payment verification

1. Authenticated as the seeded development user.
2. Created a mock payment for controlled order `338`; the endpoint returned `code=0` and a payment number.
3. Sent the first JSON callback with a unique transaction ID; the endpoint returned `code=0`, `data=PAID`.
4. Repeated the same callback; the endpoint returned `code=0`, `data=DUPLICATE`.
5. Verified the database had one callback event for order `338`, `process_result=PAID`, and one payment state transition to `order_status=1`.
6. An unknown payment number returned `code=500413` (`PAYMENT_FAILED`).

Prometheus values observed after this traffic:

- `payment_callback_total{outcome="paid"}` = `1`
- `payment_callback_total{outcome="duplicate"}` = `1`
- `payment_callback_total{outcome="invalid"}` = `1`
- `seckill_rabbit_queue_depth{queue="seckill.queue"}` = `0`
- `seckill_rabbit_queue_depth{queue="seckill.dead.queue"}` = `0`
- `http_server_requests_seconds_count` included login, payment create, and two successful callback requests.

## High-concurrency ingress run

This run intentionally kept `runSeckill=false` and `runMockPayment=false`. It exercised authenticated login and goods detail traffic without changing stock or creating orders.

| Item | Observed value |
|---|---|
| JMeter | `5.6.3` |
| JMX SHA256 | `D1D3FDA4A1B716E9A9C73B35423B41DB383E1548B46CF1BDB885D5C033FFFEE8` |
| Parameters | 500 threads, 60-second ramp, 1 loop, 1000 total requests |
| Start/end | `2026-08-27 19:19:12 - 19:20:12 +08:00` |
| Business flags | `runSeckill=false`, `runMockPayment=false` |
| HTTP results | 1000/1000 HTTP 200 and JMeter success, 0 errors |
| Throughput | 16.94 requests/second |
| Login | 500 requests, average 19.75 ms, P95 35 ms, P99 243.19 ms, max 297 ms |
| Goods detail | 500 requests, average 15.59 ms, P95 36 ms, P99 209.97 ms, max 227 ms |
| JTL | `D:\tools\jmeter-run-500-20260827\run.jtl` |
| HTML report | `D:\tools\jmeter-run-500-20260827\html\index.html` |
| Console log | `D:\tools\jmeter-run-500-20260827\jmeter.log` |

After the run, MySQL remained at 4 `order_info` rows, 2 callback audit rows, and stock 10; Redis `GoodsKey:gs1` remained 10; RabbitMQ ready/unacknowledged messages remained 0/0.

## Higher-concurrency ingress run

The 1000-thread run also kept `runSeckill=false` and `runMockPayment=false`, so it did not consume inventory or create orders.

| Item | Observed value |
|---|---|
| Parameters | 1000 threads, 60-second ramp, 1 loop, 2000 total requests |
| Start/end | `2026-08-27 19:21:56 - 19:22:56 +08:00` |
| HTTP results | 2000/2000 HTTP 200 and JMeter success, 0 errors |
| Throughput | 33.92 requests/second |
| Login | 1000 requests, average 29.96 ms, P95 26.9 ms, P99 612.98 ms, max 669 ms |
| Goods detail | 1000 requests, average 31.56 ms, P95 20 ms, P99 836.97 ms, max 921 ms |
| JTL | `D:\tools\jmeter-run-1000-20260827\run.jtl` |
| HTML report | `D:\tools\jmeter-run-1000-20260827\html\index.html` |
| Console log | `D:\tools\jmeter-run-1000-20260827\jmeter.log` |

Post-run checks remained healthy: application health `UP`, all five Prometheus targets `up=1`, MySQL counts `order_info=4`, `payment_callback_event=2`, `seckill_goods.stock_count=10`, Redis `GoodsKey:gs1=10`, and RabbitMQ ready/unacknowledged messages `0/0`.

## Limitations

JMeter is installed. A non-destructive 1-thread smoke run, a 500-thread ingress-only run, and a 1000-thread ingress-only run completed with zero errors. The 500-thread and 1000-thread JTL/HTML reports are retained outside the repository. No seckill order-creation, oversell, or full seckill business-outcome result was produced because CAPTCHA-gated path provisioning was intentionally not automated. The final decision therefore remains `INCONCLUSIVE` until a scoped reset and an approved path-backed seckill run are completed.
