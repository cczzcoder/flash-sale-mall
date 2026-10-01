# Observability deployment examples

This directory is self-contained and does not change the application. It deploys Prometheus, Blackbox Exporter, and Grafana. Versions are example pins; review image provenance and update policy before operational use.

## Current application coverage

The application now includes Spring Boot Actuator, a Micrometer Prometheus registry, custom health indicators, and business metrics. `/actuator/prometheus` is available when the app is running with the configured management endpoints. The Prometheus job `seckill-app` scrapes `host.docker.internal:8080/actuator/prometheus`; change that target when the application is deployed elsewhere. The provisioned dashboard combines outside-in HTTP probes with app-side request, queue, callback, and lifecycle metrics.

## Start

```sh
export GF_SECURITY_ADMIN_PASSWORD='replace-with-a-long-random-secret'
docker compose -f observability/docker-compose.yml config
docker compose -f observability/docker-compose.yml up -d
```

Open Grafana at `http://localhost:3000` and Prometheus at `http://localhost:9090`. On Linux, `host.docker.internal` is mapped by Compose. If the application runs elsewhere, update blackbox targets in `prometheus/prometheus.yml`. Restrict all ports and exporter credentials at the network boundary; these examples are for a controlled test network.

Validate before a load test:

```sh
curl -fsS http://localhost:9090/-/ready
curl -fsS 'http://localhost:9115/probe?module=http_2xx&target=http://host.docker.internal:8080/login/index'
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/prometheus | grep -E 'http_server_requests|payment_callback|order_timeout|seckill_rabbit'
```

## Alert rules

`prometheus/rules.yml` includes example rules for probe failure, slow probe, and scrape failure. The one-second latency threshold is explicitly an example, not an observed baseline or SLO. Tune thresholds from measured normal behavior and a documented service objective. Connect Alertmanager separately if notifications are required; Prometheus can evaluate rules without delivering notifications.

## Exporter extensions without application edits

Add only exporters actually deployed and secured, then uncomment corresponding scrape jobs:

- Node Exporter: host CPU, memory, disk, and network. Run on the application host, not inside this Compose stack if host visibility is required.
- MySQL Exporter: connections, query/transaction rates, InnoDB locks/buffer pool. Use a least-privilege monitoring account.
- Redis Exporter: commands, memory, clients, keyspace, evictions. Do not expose Redis credentials in committed files.
- RabbitMQ Prometheus plugin: ready/unacknowledged messages, publish/deliver rates, consumers. Confirm metric names for the deployed RabbitMQ version.
- JMX Exporter: JVM/process metrics without changing Java source, but it requires an explicitly configured Java agent and compatible rules.

Exporter metric names vary by version. Inspect `/metrics` in the actual environment before importing queries; do not fabricate missing series.

## Recommended dashboard structure

Keep each measure on one axis and retain a table view:

1. Availability stat from `probe_success`, clearly labeled as synthetic HTTP availability.
2. Probe duration time series from `probe_duration_seconds`.
3. Probe phase time series from `probe_http_duration_seconds`.
4. Scrape health from `up`.
5. Separate rows, once exporters exist, for host, MySQL, Redis, and RabbitMQ. Do not combine unlike units on a dual axis.
6. JMeter remains the source for request-level percentiles and parsed business outcomes; the app-side panels provide a second signal for server throughput, latency, and business lifecycle counters.

Suggested application metrics to keep or extend:

- request count/latency by normalized route and outcome, never raw path/token/user labels;
- seckill accepted, sold-out, duplicate, rate-limited, and order-created counters;
- MQ publish failures and consumer processing latency;
- stock rollback/reconciliation counters and DB-vs-Redis divergence gauge;
- mock payment create/callback/idempotency outcomes, explicitly labeled `provider="MOCK"`;
- datasource active/max connections and JVM GC/heap/thread metrics.

Avoid high-cardinality labels such as user ID, order ID, token, payment number, transaction ID, random seckill path, exception message, or unbounded URL. Do not scrape authenticated business endpoints with credentials from Prometheus. The blackbox targets here are public read-only endpoints.

## Correlating a run

Record the exact test start/end timestamps and Grafana timezone. Preserve JMeter JTL/HTML artifacts and Prometheus snapshots or query links for that interval. Treat HTTP 200 separately from `Result.code=0`; Blackbox Exporter cannot see JSON business success. Use `load-test/report/REPORT_TEMPLATE.md` to distinguish observed values, acceptance targets, and unknowns.
