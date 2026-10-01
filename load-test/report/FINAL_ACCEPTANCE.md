# Final acceptance record

This document is the closeout checklist for the seckill tail work. It separates repository evidence from environment-specific observations. A checked repository item does not mean a load-test run passed.

## Verified in this workspace

- [x] `mvn test -q` passed.
- [x] `mvn package -DskipTests` completed after the current changes and the application was restarted from the rebuilt `target/seckill.jar`.
- [x] Grafana dashboard JSON parsed successfully.
- [x] `order_detail.htm` inline JavaScript passed Node syntax checking.
- [x] Micrometer naming check confirmed `payment.callback.total` is exposed as `payment_callback_total`.

The checks below still require the deployed application and its test dependencies unless explicitly marked as repository-only.

## Repository evidence

- [x] `mvn test -q` passes on the candidate revision.
- [x] `mvn package -DskipTests` produces the deployable artifact.
- [x] `application.properties` exposes health, metrics, and Prometheus endpoints as intended.
- [x] Prometheus `seckill-app` target is configured for the default local application address.
- [x] Grafana dashboard is provisioned and shows both Blackbox and app-side panels.
- [x] Dashboard queries return live data for `up`, `http_server_requests_seconds_*`, `seckill_rabbit_queue_depth`, and `payment_callback_total` after controlled traffic. No `order_timeout_close_total` sample was observed in the final runtime window; the historical expired orders had already been closed before the final restart.
- [x] Database migration/schema for `payment_callback_event` has been applied in the test environment.
- [x] V3/V4 database migrations have been applied to the new local `seckill` database; delivery-address, admin-user, and order-address snapshot schema is present.
- [x] Payment UI sends `paymentNo` and a unique `transactionId` as JSON to `/payment/mock/callback`.
- [x] Repository gap review and staged implementation status are recorded in [`TAIL_WORK_STATUS.md`](TAIL_WORK_STATUS.md).

## Environment evidence

- [x] Application health is `UP`; Redis and RabbitMQ connectivity are verified.
- [x] Current runtime smoke checks passed for `/login/index`, `/register.htm`, `/address.htm`, `/admin.htm`, and `/goods/detailStatic/1`; unauthenticated `/address/list` and `/admin/goods/list` returned the expected rejection business codes.
- [x] Prometheus targets are up for `prometheus`, `seckill-app`, `seckill-blackbox`, and the deployed exporters.
- [x] Blackbox probes were available throughout the controlled verification window.
- [x] Test data was reset with the scoped procedure; DB stock equals Redis stock before traffic (goods 1, 100 isolated users).
- [x] JMeter JTL, HTML report, console log, JMX checksum, revision, and exact start/end timestamps are retained for the 500-thread ingress-only run. The artifacts are outside the repository under `D:\\tools\\jmeter-run-500-20260827`.
- [x] Business outcomes were parsed separately from HTTP status for the fresh path-backed run; 100 submissions were accepted and 100 orders were created, with no duplicates or sold-out responses.
- [x] RabbitMQ ready and unacknowledged messages are zero after drain; the dead-letter queue was inspected and is empty.
- [x] Final DB/Redis/order consistency arithmetic is recorded in [`stage4b-20260828-132553.md`](stage4b-20260828-132553.md): `100 - 100 = 0`.

## Payment acceptance

- [x] A controlled unpaid order can create a mock payment and return a payment number.
- [x] The first callback returns `PAID` and the order becomes paid.
- [x] Repeating the same transaction returns `DUPLICATE` without a second state transition.
- [x] Callback for an unknown payment number fails with the expected business error. A closed-order callback was not separately exercised.
- [x] The payment flow is explicitly reported as mock-only and is not treated as real gateway validation.

## Decision

| Field | Value |
|---|---|
| Candidate revision | `5be45b4 + uncommitted working-tree changes` |
| Test report | [`LOCAL_RUNTIME_EVIDENCE.md`](LOCAL_RUNTIME_EVIDENCE.md) |
| Observability time range | `2026-08-28 13:25:56 - 13:26:04 +08:00` |
| Decision | `PASS for the scoped 100-user business consistency run; no production SLA claim` |
| Owner and date | `Codex, 2026-08-28` |
| Exceptions and follow-ups | `The fresh run used pre-issued local loadtest paths and isolated users. It validates order creation, no oversell, queue drain, and DB/Redis arithmetic. It does not validate CAPTCHA automation, real payment, or production-scale capacity.` |

PASS requires all applicable repository and environment checks to be evidenced. Use INCONCLUSIVE when a dependency, exporter, business outcome, or consistency check was not observed rather than guessing.
