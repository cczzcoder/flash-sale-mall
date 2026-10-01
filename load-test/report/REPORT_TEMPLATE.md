# Load-test report: <run ID>

> Template only. Replace placeholders from retained evidence. Do not estimate missing values or present targets as observed results.

## Run metadata

| Field | Value |
|---|---|
| Date/time and timezone | `<value>` |
| Tester / change under test | `<value>` |
| Application revision and working-tree state | `<value>` |
| Environment and topology | `<value>` |
| JMeter version / generator hosts | `<value>` |
| Test plan and checksum | `<value>` |
| Parameter file description (no secrets) | `<value>` |
| Scenario flags | `runSeckill=<...>, runMockPayment=<...>` |
| Goods ID / initial DB and Redis stock | `<value>` |
| User range / unique user count | `<value>` |
| Threads, ramp, loops, duration | `<value>` |

## Objective and acceptance criteria

- Objective: `<what decision this run supports>`
- In scope: `<endpoints and dependencies>`
- Out of scope: `<for example CAPTCHA OCR or real payment gateway>`
- Predeclared acceptance criteria: `<latency, error, throughput, consistency targets>`

## Scenario notes

State whether seckill paths were prepared through the normal CAPTCHA flow. **CAPTCHA automation/bypass is not covered by these assets.** Record path provisioning time because paths expire after 60 seconds and are one-time-use.

State whether payment was exercised. **`/payment/mock/*` is a mock-payment scenario only:** no real funds, provider signatures, fraud controls, gateway latency, or provider webhook availability. Describe callback JSON/idempotency checks separately from purchase throughput.

## Observed workload

| Sample label | Requests | Throughput | Errors | Error % | Mean | Median | p90 | p95 | p99 | Max |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `<label>` | `<observed>` | `<observed>` | `<observed>` | `<observed>` | `<observed>` | `<observed>` | `<observed>` | `<observed>` | `<observed>` | `<observed>` | `<observed>` |

Source artifacts: `<JTL, JMeter HTML report, Prometheus snapshot URLs/time range>`.

## Business outcomes

Do not equate HTTP 200 with success. Count parsed response codes/data independently.

| Outcome | Count | Evidence/query |
|---|---:|---|
| Login success / failure by business code | `<observed>` | `<source>` |
| Seckill queued (`code=0,data=0`) | `<observed>` | `<source>` |
| Order created (`result data > 0`) | `<observed>` | `<source>` |
| Still queued (`result data = 0`) | `<observed>` | `<source>` |
| Sold out (`result data = -1` or stock error) | `<observed>` | `<source>` |
| Duplicate seckill | `<observed>` | `<source>` |
| Access limited (`code=500104`) | `<observed>` | `<source>` |
| Session invalid (`code=500210`) | `<observed>` | `<source>` |
| Mock payment PAID / DUPLICATE / failed | `<observed or N/A>` | `<source>` |

## System observations

| Signal | Before | Peak/Range | After | Evidence |
|---|---:|---:|---:|---|
| Blackbox probe availability/latency | `<observed>` | `<observed>` | `<observed>` | `<source>` |
| Host CPU/memory/disk/network | `<observed>` | `<observed>` | `<observed>` | `<source>` |
| MySQL connections/QPS/locks/slow queries | `<observed>` | `<observed>` | `<observed>` | `<source>` |
| Redis ops/memory/latency/keyspace | `<observed>` | `<observed>` | `<observed>` | `<source>` |
| RabbitMQ ready/unacked/publish/deliver | `<observed>` | `<observed>` | `<observed>` | `<source>` |
| JVM metrics | `<N/A unless separately exported>` | `<value>` | `<value>` | `<source>` |

## Consistency checks

Record exact SQL/Redis/RabbitMQ evidence and timing.

- DB `seckill_goods.stock_count`: `<observed>`
- Redis `GoodsKey:gs<goodsId>`: `<observed>`
- Created `seckill_order` rows in scope: `<observed>`
- Created `order_info` rows in scope: `<observed>`
- Duplicate `(user_id, goods_id)` rows: `<observed>`
- RabbitMQ ready/unacked after drain: `<observed>`
- Invariant assessment (`initial stock - successful orders = final stock`): `<pass/fail with arithmetic>`

## Errors and anomalies

| Timestamp | Layer | Symptom | Count | Correlated signals | Evidence |
|---|---|---|---:|---|---|
| `<value>` | `<JMeter/app/DB/Redis/MQ>` | `<value>` | `<observed>` | `<value>` | `<source>` |

## Findings and decision

- Findings supported by evidence: `<value>`
- Limitations/confounders: `<value>`
- Decision: `<pass/fail/inconclusive against predeclared criteria>`
- Follow-up experiments: `<value>`
